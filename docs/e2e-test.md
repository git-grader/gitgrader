# End-to-end test

A scripted pass over the whole product on one machine: containers up, an instructor
working in a browser, a student registering and pushing signed work over SSH, grading in
a throwaway sandbox, and the refusals that matter probed one by one. Every step states
what to expect, so a run either matches or has found something.

This is the acceptance test for a release candidate and the first thing to run when "it
works on my machine" is in doubt. It complements, and does not replace, `./mvnw clean
verify`: that proves the code, this proves the deployment. The campaign that produced this
document found six defects, listed at the end — every one of them passed the unit and
integration suites.

The sample assignment ships a reference solution that deliberately fails three of its ten
hidden checks, so the whole run has one arithmetic oracle: **7 of 10, 70.0 %**. Any other
number is a finding.

Budget about 40 minutes for the full sweep, most of it the first image build.

## 0. Preconditions

| Requirement | Check | Why |
|---|---|---|
| Docker + Compose plugin | `docker info`, `docker compose version` | runs everything |
| JDK 25 or newer | `java -version` | builds the image from this checkout |
| `git`, `ssh-keygen`, `curl`, `awk`, `jq`, `python3` | `command -v` each | drives the student path |
| Ports 8080, 2222, 5432 free | `ss -ltn` | the stack binds them |
| ~5 GB disk | `df -h .` | image, volumes, runtime image |

**The daemon must share its filesystem with this host.** The grading sandbox is a sibling
container that the daemon bind-mounts from an absolute path, so a daemon running in a VM
or its own mount namespace (Docker Desktop, Colima, rootless) resolves that path to
something else and would grade every submission against empty directories.
`scripts/install.sh` proves this and stops if it cannot. To check by hand:

```sh
docker volume create probe-vol >/dev/null
docker run --rm -v probe-vol:/v alpine:3.20 sh -c 'touch /v/marker'
docker run --rm -v "$(docker volume inspect probe-vol --format '{{.Mountpoint}}')":/v \
  alpine:3.20 sh -c 'test -f /v/marker && echo "daemon shares the host filesystem" || echo "UNUSABLE for grading"'
docker volume rm probe-vol >/dev/null
```

If it prints `UNUSABLE`, give the two sandbox volumes host paths the daemon can see and
point the mount roots at them:

```yaml
# /tmp/e2e/compose.e2e.yaml, added with a third -f
# Both services need the same two paths: the app writes a workspace and the runner hands
# that path to the daemon, which resolves it on the host.
services:
  app:
    volumes:
      - /tmp/e2e/data/grading:/data/grading
      - /tmp/e2e/data/tests:/data/tests
  runner:
    volumes:
      - /tmp/e2e/data/grading:/data/grading
      - /tmp/e2e/data/tests:/data/tests
```

Start from a clean slate, or step 6 will read results from an earlier run:

```sh
docker compose -f compose.yaml -f compose.dev.yaml down -v
```

## 1. Install

```sh
./scripts/install.sh --demo
```

**Expect** the last lines to name the web and SSH endpoints, and:

```
==> Pointing the grading sandbox at the volumes
    Sandbox reads submissions from /var/lib/docker/volumes/gitgrader_grading-data/_data
    The daemon can see them.
```

The demo runtime is pinned by digest and is not pulled by the install. Pull it once, or
the first submission fails with `No such image`:

```sh
docker pull "$(docker compose -f compose.yaml -f compose.dev.yaml exec -T database \
  psql -U gitgrader -d gitgrader -tAc \
  "select image || '@' || image_digest from runtimes where enabled" | tr -d ' \r')"
```

## 2. The service answers

```sh
./scripts/verify-install.sh
curl -s http://localhost:8080/actuator/health | jq -c .
curl -s http://localhost:8080/api/v1/meta | jq -c .
docker compose -f compose.yaml -f compose.dev.yaml exec -T database psql -U gitgrader -d gitgrader \
  -tAc 'select version, success from flyway_schema_history order by installed_rank desc limit 1'
```

**Expect** `Installation checks passed`, `{"status":"UP"}` — not `DOWN`, because health has
to be usable as a probe — meta naming `sshPort 2222`, and the newest migration applied.

**Expect** the settings you put in `.env` to reach the application. Compose passes only
what it names, which is worth checking whenever a setting appears not to work:

```sh
docker inspect gitgrader-app-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep SECURITY_
```

## 3. A student registers

```sh
rm -rf /tmp/e2e && mkdir -p /tmp/e2e
ssh-keygen -t ed25519 -f /tmp/e2e/student -N "" -q -C alan@example.org

curl -s -c /tmp/e2e/jar -o /dev/null http://localhost:8080/api/v1/meta
CSRF=$(awk '/XSRF-TOKEN/{print $NF}' /tmp/e2e/jar)

curl -s -b /tmp/e2e/jar -X POST http://localhost:8080/api/v1/registration \
  -H "X-XSRF-TOKEN: $CSRF" -H 'Content-Type: application/json' \
  -w '\nHTTP %{http_code}\n' \
  -d "$(python3 - <<'EOF'
import json
print(json.dumps({"firstName":"Alan","lastName":"Turing","studentNumber":"s2001",
 "email":"alan@example.org","courseKey":"example-programming","classKey":"main",
 "publicKey":open("/tmp/e2e/student.pub").read().strip()}))
EOF
)"
```

**Expect** `HTTP 201` and a `keyFingerprint`. Then, after a moment for the event to be
handled:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database psql -U gitgrader -d gitgrader \
  -tAc 'select count(*) from repositories'
docker compose -f compose.yaml -f compose.dev.yaml exec -T database psql -U gitgrader -d gitgrader \
  -tAc 'select s.student_number, c.class_key, e.status from enrollments e
          join students s on s.id = e.student_id
          left join course_classes c on c.id = e.class_id'
```

**Expect** 12 repositories, one per assignment, **and one enrolment row** naming the class
the student picked. A student with repositories and no enrolment is invisible to every
course report.

### Refusals worth checking

| Attempt | Expect |
|---|---|
| the same student number again | `409` |
| the same public key, new number | `400`, telling the student to generate a new key |
| a **private** key pasted in | `400`, and the response must not echo the key material |
| an unknown course key | refused, without confirming which courses exist |
| empty or malformed fields | `400` naming the fields |
| no `X-XSRF-TOKEN` header | `403` as `application/problem+json` — never a redirect, and never a session id in a URL |

## 4. Clone, and prove the hidden tests are not in it

```sh
export GIT_SSH_COMMAND="ssh -i /tmp/e2e/student -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes"
git clone ssh://git@localhost:2222/example-programming/assignment-01-string-utils/s2001.git /tmp/e2e/work

find /tmp/e2e/work -iname '*hidden*' -not -path '*/.git/*'
```

**Expect** the clone to contain `src`, `public-tests`, `package.json`, and the `find` to
print **nothing**. Anything printed is a serious defect: the assessment secrets shipped to
the student.

## 5. What admission refuses

Set the identity once:

```sh
gitgrader="$PWD"          # the checkout, before changing directory
cd /tmp/e2e/work
git config user.name "Alan Turing"; git config user.email alan@example.org
git config gpg.format ssh; git config user.signingkey /tmp/e2e/student
git config commit.gpgsign true
cp "$gitgrader"/examples/assignments/assignment-01-string-utils/reference-solution/partial-70/string-utils.js src/string-utils.js
```

| Attempt | Expect |
|---|---|
| unsigned commit, then `git push origin main` | rejected, naming the commit and the three `git config` lines that fix it |
| a key registered to nobody | permission denied, no repository listing |
| another student's repository, with your own key | refused, without revealing whether it exists |
| a commit signed with another student's registered key | rejected as signed by a key registered to a different student |
| `git push origin --delete main` | rejected; history has to stay reconstructible |
| `git push origin +main:main` after a reset | rejected as non-fast-forward |
| `git tag -m x v1 && git push origin refs/tags/v1` | rejected: only `refs/heads/` is accepted |

## 6. Push signed work

```sh
git commit -q --amend -S -m "Implement string utilities"
git push origin main
```

**Expect** the server's feedback block:

```
remote: Signature: Verified
remote: Submission accepted.
remote: http://localhost:8080/result/<token>
```

Keep that URL. Then watch grading finish:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database psql -U gitgrader -d gitgrader \
  -tAc 'select status, score_percent, tests_passed, tests_total from grading_runs order by created_at desc limit 1'
```

**Expect** `COMPLETED | 70.000 | 7 | 10` within a few seconds of the sandbox starting.

| Instead you saw | It means |
|---|---|
| `INFRASTRUCTURE_ERROR`, log says `No such image` | the runtime image was never pulled (step 1) |
| `INFRASTRUCTURE_ERROR`, "not visible inside the sandbox" | the daemon cannot resolve the mount roots (preconditions) |
| `COMPLETED` but `0/10` | on a build before the mount probe existed, the same mount problem |
| any other score | a real grading regression |

**Expect** the submission to carry what admission learned:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database psql -U gitgrader -d gitgrader \
  -tAc 'select status, signature_status, signature_key_id is not null, git_ref from submissions order by created_at desc limit 1'
```

**Expect** `PASSED | VERIFIED | t | refs/heads/main`.

**Regrade** it through `POST /api/v1/submissions/{id}/regrade` as an instructor. **Expect**
`202` and a second `grading_runs` row. A regrade requested while a run is still active is
refused rather than started alongside it.

## 7. The instructor interface

Open <http://localhost:8080/> and sign in as `instructor` / `password`.

| Page | Expect |
|---|---|
| `/login` | a wrong password says the credentials were not accepted; the right one lands on `/dashboard` |
| `/dashboard` | counts that match the database, not zeros |
| `/courses` → New Course | required fields are enforced before submitting; creating one lists it |
| `/submissions` | the pushed commit, **Passed**, Signature **VERIFIED** |
| `/courses` → the course | classes and enrolments load; a failed load says so rather than showing an empty table |
| `/reports/courses/{id}` | the enrolled students appear, and CSV, JSON and XLSX each download **without leaving the page** |
| `/admin/audit` | refused with "Administrators only", naming the signed-in account |
| sign out | returns to `/login`; afterwards `curl /api/v1/me` is `401` |

Keep the browser console open. **Expect** no errors.

## 8. The student's result page

Open the result URL from step 6 **in a browser with no session**.

**Expect**: the assignment and course names, `Verified` with the caveat that it does not
certify how the work was produced, **7 of 10 tests passed**, **Score: 70.0 %**, and ten
rows naming a *category* and a *hint*.

**Expect not**: the name of any hidden test, any assertion text, any file path, any
instructor-only field:

```sh
curl -s http://localhost:8080/api/v1/results/<token> | grep -ciE 'h0[0-9]|hidden\.test|assert'
```

**Expect** `0`, and the response headers to carry `Referrer-Policy: no-referrer`,
`Cache-Control: no-store` — the link is the whole credential — `X-Frame-Options: DENY` and
a `default-src 'none'` CSP. An altered token answers `404`, the same as one that never
existed.

## 9. Authorization and exposure

| Probe | Expect |
|---|---|
| any `/api/v1/**` without a session | `401` problem+json, never a redirect |
| `/api/v1/audit` as an instructor | `403` |
| `POST /api/v1/runtimes` as an instructor | `403` |
| revoking a key through another student's URL | `404`, and the key untouched |
| revoking an extension through another assignment's URL | `404` |
| `/actuator/metrics` with no credentials | `401` with `WWW-Authenticate: Basic` — a redirect here silently breaks Prometheus |
| `/actuator/metrics` with admin credentials | `200` |
| `/api/v1/courses/not-a-uuid` | `400`, carrying no stack trace, class name or SQL |

## 10. Operations

```sh
# The host key must not change, or every student gets a host key warning.
ssh-keyscan -p 2222 -t ecdsa localhost | awk '{print $3}'
docker compose -f compose.yaml -f compose.dev.yaml restart app
ssh-keyscan -p 2222 -t ecdsa localhost | awk '{print $3}'

./scripts/backup.sh /tmp/e2e/backup
```

**Expect** an identical host key, the submission still present, readiness and health both
`200`, and a backup directory with a checksum beside it.

## 11. Tear down

```sh
docker compose -f compose.yaml -f compose.dev.yaml down -v
rm -rf /tmp/e2e
docker ps -a --filter name=gitgrader
docker volume ls | grep gitgrader
```

**Expect** both listings empty.

## What this run has proven

Signed-push admission and each of its refusals; registration validation, enrolment and
repository provisioning; hidden tests reaching the sandbox and never the student; grading
in a throwaway container with the documented score; the submission record keeping the
signing key and the real ref; instructor authentication, authorisation and the admin
boundary; the token-only result page with its headers; and that a restart changes neither
the host key nor the data.

It does not cover: LDAP over TLS (the demo directory is plaintext on purpose), a restore
onto a running instance, upgrades across versions, or more than one grading worker under
load. Those need their own runs.

## Defects this playbook has found

Each was found by running the steps above against a real deployment, and each is fixed.
They are listed because they are the shapes of failure this test exists to catch.

| # | Step | Defect |
|---|---|---|
| 1 | 2 | `compose.yaml` forwarded none of the documented `SECURITY_*`, `APP_*`, `GIT_*` or `GRADING_*` settings. An operator following `docs/installation.md` to configure LDAP got an application that never saw the URL, base DN or credentials. |
| 2 | 3 | Repository provisioning refused a directory that already existed, so anything failing after the directory was created — or a database restored from an older backup — left the student permanently unprovisionable: the registration event retried onto the same directory forever. |
| 3 | 3 | A CSRF failure answered `302` to the sign-in page with the session id in the URL path, instead of `403 problem+json`. `fetch` follows the redirect and parses a login page as the answer. |
| 4 | 2 | `/actuator/health` was permanently `503`: Boot's LDAP indicator reads `spring.ldap.urls`, which this application does not use, so it probed `localhost:389` on every deployment that did configure a directory. |
| 5 | 9 | `/actuator/metrics` answered an unauthenticated scrape with a redirect rather than a `401` challenge, so Prometheus stored the sign-in page as the metrics response and reported nothing. |
| 6 | 3, 7 | A self-registered student was never enrolled on the course. `CourseAdministration.enroll` existed with no caller and no endpoint, so every course report — the instructor's only view of how a class is doing — listed nobody. |
