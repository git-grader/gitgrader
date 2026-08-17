# End-to-end test

A scripted pass over the whole product on one machine: containers up, a student
registering and pushing signed work over SSH, grading in a throwaway sandbox, and both
web surfaces in a real browser. Every step states what to expect, so a run either matches
or has found something.

This is the acceptance test for a release candidate and the first thing to run when
"it works on my machine" is in doubt. It complements, and does not replace,
`./mvnw clean verify`: that proves the code, this proves the deployment.

The sample assignment ships a reference solution that deliberately fails three of its ten
hidden checks, so the whole run has one arithmetic oracle: **7 of 10, 70.0 %**. Any other
number is a finding.

Budget about 20 minutes, most of it the first image build.

## 0. Preconditions

| Requirement | Check | Why |
|---|---|---|
| Docker + Compose plugin | `docker info`, `docker compose version` | runs everything |
| JDK 25 | `java -version` | builds the image from this checkout |
| `git`, `ssh-keygen`, `curl`, `awk`, `jq`, `python3` | `command -v` each | drives the student path |
| Ports 8080, 2222, 5432 free | `ss -ltn` | the stack binds them |
| ~5 GB disk | `df -h .` | image, volumes, runtime image |

**The daemon must share its filesystem with this host.** The grading sandbox is a sibling
container that the daemon bind-mounts from an absolute path, so a daemon running in a VM
or its own mount namespace (Docker Desktop, Colima, rootless) resolves that path to
something else and grades every submission against empty directories. `install.sh` proves
this before it finishes and stops if it cannot; to check by hand:

```sh
docker volume create probe-vol >/dev/null
docker run --rm -v probe-vol:/v alpine:3.20 sh -c 'touch /v/marker'
docker run --rm -v "$(docker volume inspect probe-vol --format '{{.Mountpoint}}')":/v \
  alpine:3.20 sh -c 'test -f /v/marker && echo "daemon shares the host filesystem" || echo "UNUSABLE for grading"'
docker volume rm probe-vol >/dev/null
```

Start from a clean slate, or step 6 will read results from an earlier run:

```sh
docker compose -f compose.yaml -f compose.dev.yaml down -v
rm -f .env          # only if you want the installer to write a fresh one
```

> A `.env` kept from an older checkout is the single most common cause of a run that
> looks healthy and grades everything zero: it names volumes that no longer exist. The
> installer now overwrites the two sandbox mount roots from the live volumes on every
> run, so keeping `.env` is safe, but a fresh one removes the question.

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

**Then** `docker compose -f compose.yaml -f compose.dev.yaml ps` shows `app`, `database`
(healthy) and `openldap` running.

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
curl -s http://localhost:8080/actuator/health/readiness | jq -c .
curl -s http://localhost:8080/api/v1/meta | jq -c .
```

**Expect** `Installation checks passed`, `{"status":"UP"}`, and meta naming
`sshPort 2222` and `registrationEnabled true`.

**Expect** the schema at its newest migration:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database \
  psql -U gitgrader -d gitgrader -tAc \
  'select version, success from flyway_schema_history order by installed_rank desc limit 1'
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

**Expect** `HTTP 201` and a `keyFingerprint`. Repositories are provisioned from the
registration event, so allow a moment:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database \
  psql -U gitgrader -d gitgrader -tAc 'select count(*), status from repositories group by status'
```

**Expect** `12 | READY`, one per assignment in the sample course.

## 4. Clone, and prove the hidden tests are not in it

```sh
export GIT_SSH_COMMAND="ssh -i /tmp/e2e/student -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes"
git clone ssh://git@localhost:2222/example-programming/assignment-01-string-utils/s2001.git /tmp/e2e/work

find /tmp/e2e/work -iname '*hidden*' -not -path '*/.git/*'
```

**Expect** the clone to contain `src`, `public-tests`, `package.json`, and the `find` to
print **nothing**. Anything printed is a serious defect: the assessment secrets shipped to
the student.

## 5. Admission refuses what it should

Set the identity once:

```sh
gitgrader="$PWD"          # the checkout, before changing directory
cd /tmp/e2e/work
git config user.name "Alan Turing"; git config user.email alan@example.org
git config gpg.format ssh; git config user.signingkey /tmp/e2e/student
git config commit.gpgsign true
cp "$gitgrader"/examples/assignments/assignment-01-string-utils/reference-solution/partial-70/string-utils.js src/string-utils.js
```

**Unsigned commit — must be refused:**

```sh
git -c commit.gpgsign=false commit -qam "unsigned attempt"
git push origin main
```

**Expect** `remote rejected`, a message naming the commit as unsigned, and the three
`git config` lines that fix it.

**Unknown key — must not authenticate:**

```sh
ssh-keygen -t ed25519 -f /tmp/e2e/stranger -N "" -q
GIT_SSH_COMMAND="ssh -i /tmp/e2e/stranger -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes -o BatchMode=yes" \
  git ls-remote origin
```

**Expect** a permission failure, not a repository listing.

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

Keep that URL; step 8 opens it. Then watch grading finish:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database \
  psql -U gitgrader -d gitgrader -tAc \
  'select status, score_percent, tests_passed, tests_total from grading_runs order by created_at desc limit 1'
```

**Expect** `COMPLETED | 70.000 | 7 | 10` within a few seconds of the sandbox starting.

| Instead you saw | It means |
|---|---|
| `INFRASTRUCTURE_ERROR`, log says `No such image` | the runtime image was never pulled (step 1) |
| `INFRASTRUCTURE_ERROR`, "not visible inside the sandbox" | the daemon cannot resolve the mount roots (preconditions) |
| `COMPLETED` but `0/10` | on a build before that check existed, the same mount problem |
| any other score | a real grading regression |

**Expect** the submission to carry what admission learned:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database \
  psql -U gitgrader -d gitgrader -tAc \
  'select status, signature_status, signature_key_id is not null, git_ref from submissions order by created_at desc limit 1'
```

**Expect** `PASSED | VERIFIED | t | refs/heads/main`. A null key id or a `git_ref` that is
always `refs/heads/main` regardless of the branch pushed are both regressions.

## 7. The instructor interface

Open <http://localhost:8080/> and sign in as `instructor` / `password`.

| Page | Expect |
|---|---|
| `/login` | signing in lands on `/dashboard`; a wrong password says the credentials were not accepted, not a generic failure |
| `/dashboard` | 1 course, 1 student, 1 open assignment, 0 running |
| `/submissions` | one row: commit `fe1bd12`, **Passed**, Signature **VERIFIED**, the commit message |
| `/courses` → the course | classes and enrolments load; a failed load says so rather than showing an empty table |
| `/admin/audit` | **refused** with "Administrators only", naming the signed-in account. An instructor must not see the audit log, and must not see a broken page either |
| sign out | returns to `/login`; afterwards `curl http://localhost:8080/api/v1/me` is `401` |

Keep the browser console open. **Expect** no errors other than the CSP notice
`'script-src' was not explicitly set` (informational, from the vendor bundle).

## 8. The student's result page

Open the result URL from step 6 **in a browser with no session**.

**Expect**: the assignment and course names, `Verified` with the caveat that it does not
certify how the work was produced, **7 of 10 tests passed**, **Score: 70.0 %**, and ten
rows naming a *category* and a *hint*.

**Expect not**: the name of any hidden test, any assertion text, any file path, any
instructor-only field. Grep the API for it:

```sh
curl -s http://localhost:8080/api/v1/results/<token> | grep -ciE 'h0[0-9]|hidden\.test|assert'
```

**Expect** `0`.

**Expect** the response headers to carry `Referrer-Policy: no-referrer` (the token is in
the URL), `X-Frame-Options: DENY` and a `default-src 'none'` CSP, and an invalid token to
answer `404` — the same as a valid token that does not exist, so the endpoint cannot be
used to discover which tokens are real.

## 9. Tear down

```sh
docker compose -f compose.yaml -f compose.dev.yaml down -v
rm -rf /tmp/e2e
docker ps -a --filter name=gitgrader
docker volume ls | grep gitgrader
```

**Expect** both listings empty. `-v` discards the volumes, which is what makes the next
run a genuine first run.

## What this run has proven

Signed-push admission, the registered-key rule and its refusals; repository provisioning
from a registration; hidden tests reaching the sandbox and never the student; grading in a
throwaway container with the documented score; the submission record keeping the signing
key and the real ref; instructor authentication, authorisation and the admin boundary; and
the token-only result page with its headers.

It does not cover: LDAP over TLS (the demo directory is plaintext on purpose), the
production profile's refusals, backup and restore, upgrades across versions, or more than
one grading worker under load. Those need their own runs.
