# Manual testing

A walkthrough of the whole path a course takes: an instructor signs in, a student
registers, pushes signed work, and reads their result. Every command here was run
against a clean stack.

The sample assignment ships with a reference solution that deliberately fails three
of its ten hidden checks, so the expected outcome is **7 of 10, 70.0 %**. A different
number means something is wrong, which is more useful than a pass/fail.

## Start

Needs Docker, and JDK 25 the first time so the image can be built. The commands below
also use `git`, `ssh-keygen`, `curl`, `awk` and `python3`, which a minimal container host
does not necessarily have.

```sh
./scripts/install.sh --demo
```

That brings everything up on <http://localhost:8080> with the sample course
loaded: the catalogue, the starter project and the hidden tests. It takes a few
minutes the first time, mostly building the image.

## Sign in as an instructor

Open <http://localhost:8080/> and sign in as `instructor` / `password`. The account
comes from `deployment/ldap/bootstrap.ldif` and is a member of
`gitgrader-instructors`, so the interface should offer courses, assignments,
submissions and reports.

Local accounts are refused under the production profile on purpose, so the directory
is the only way in. `admin-user` / `password` is an administrator.

## Register as a student

Registration is public. Generate a key, then register at
<http://localhost:8080/register>, or from a shell:

```sh
ssh-keygen -t ed25519 -f /tmp/student -N ""

curl -s -c /tmp/j http://localhost:8080/api/v1/meta > /dev/null
CSRF=$(awk '/XSRF-TOKEN/{print $NF}' /tmp/j)

curl -s -b /tmp/j -X POST http://localhost:8080/api/v1/registration \
  -H "X-XSRF-TOKEN: $CSRF" -H 'Content-Type: application/json' \
  -d "$(python3 - <<EOF
import json
print(json.dumps({"firstName":"Alan","lastName":"Turing","studentNumber":"s2001",
 "email":"alan@example.org","courseKey":"example-programming","classKey":"main",
 "publicKey":open("/tmp/student.pub").read().strip()}))
EOF
)"
```

Expect `201` and a key fingerprint. Repositories are created from the registration
event, so give it a moment; there should then be one per scheduled or open
assignment:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database \
  psql -U gitgrader -d gitgrader -tAc 'select count(*), status from repositories group by status'
```

## Push work

```sh
export GIT_SSH_COMMAND="ssh -i /tmp/student -o StrictHostKeyChecking=no -o IdentitiesOnly=yes"
gitgrader="$PWD"
git clone ssh://git@localhost:2222/example-programming/assignment-01-string-utils/s2001.git /tmp/work
```

The clone contains the starter project and the public tests. It must **not** contain
the hidden tests; if `find /tmp/work -name '*hidden*'` returns anything, that is a
serious defect.

Copy in the reference solution that scores 70 %, then sign and push:

```sh
cd /tmp/work
cp "$gitgrader/examples/assignments/assignment-01-string-utils/reference-solution/partial-70/string-utils.js" src/string-utils.js

git config user.name "Alan Turing"
git config user.email alan@example.org
git config gpg.format ssh
git config user.signingkey /tmp/student
git config commit.gpgsign true

git commit -aS -m "Implement string utilities"
git push origin main
```

The push should print `Signature: Verified`, `Submission accepted.` and a result
link. Worth trying deliberately: commit with `-c commit.gpgsign=false` and push, and
the server should refuse it and tell you how to enable signing.

## Read the result

Open the printed link. It needs no sign-in: the token in the URL is the credential.

Expect **7 of 10 passed** and **70.0 %**. Failing checks show their category and a
hint, never the name of the hidden check or the assertion it failed on.

Grading runs in a container, so allow a few seconds. To watch it:

```sh
docker compose -f compose.yaml -f compose.dev.yaml exec -T database \
  psql -U gitgrader -d gitgrader -tAc \
  'select status, score_percent, tests_passed, tests_total from grading_runs order by created_at desc limit 1'
```

## Worth checking too

- Push the same commit again and the submission should be rejected as already recorded.
- Push from a key that belongs to nobody and authentication should fail outright.
- `docker compose -f compose.yaml -f compose.dev.yaml restart app`, then confirm the
  SSH host key is unchanged: a changed key gives every student a host key warning.
- Sign in as an instructor and export a course report as CSV, JSON and XLSX.

## Clearing up

```sh
docker compose -f compose.yaml -f compose.dev.yaml down -v
```

`-v` discards the volumes, which is what makes the next run a genuine first run.
