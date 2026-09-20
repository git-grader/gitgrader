# GitGrader example assignments

This directory is an operator-owned source package. It is **not** a directory
to publish wholesale to students.

## Security boundary

`assignments/assignment-01-string-utils/template/` is the complete project a
student clones. Its visible files are:

- `package.json` and `package-lock.json`
- `.gitignore`, `.editorconfig`, and `.nvmrc`
- `README.md`
- `src/string-utils.js`
- `public-tests/string-utils.test.js` and its local `package.json`

The sibling `hidden-tests/` tree and the `reference-solution/` tree are
operator-only artifacts. They must be stored separately from template storage,
never copied into a repository, and never served by a student-facing endpoint.
This is a hard security boundary: once a test input or assertion reaches a
student clone, it can no longer be used to independently assess that student's
submission.

At grading time, a shimmed round splits the suite and the submission across two
containers: the sandbox (S) container copies the submitted repository to
`/workspace` and starts the shared Node shim server; the suite (T) container
mounts the operator-owned test-suite read-only at `/opt/hidden-tests`. S never
sees the suite; only the shared shim socket at `/gitgrader-shim` connects them.
GitGrader standardises this example on Node's **TAP** reporter. The grader joins
the TAP subtest names exactly to the operator manifest, then presents only its
sanitised categories and hints to students.

## Scoring

The score formula is `passed / total * 100`. All ten checks carry weight one,
so seven passing checks produce `7 / 10 * 100 = 70.0 %`. Run the proof from the
repository root (it needs Docker, and pulls the pinned runtime digest on first
use):

```sh
./examples/verify-example.sh
```

The script grades the complete implementation and the intentional 70%
implementation through the two-container shimmed path, checks TAP-to-manifest
names, and rejects any result other than 10/10 and 7/10 respectively.

## Scope

This example package intentionally contains and verifies only
`assignment-01-string-utils`. The WBE assignment packages under
`examples/assignments/wbe/` are separate course material and are not included
in this example, its seed data, or its verification script.

## Seed a running instance

First upload the template directory to the configured template store and the
operator test-suite directory to the configured test store. Update the storage
paths and content hashes in `seed-data.sql` if your storage layout differs.
Then load the idempotent records with a PostgreSQL client that targets the
GitGrader database:

```sh
psql "$DATABASE_URL" -f examples/seed-data.sql
```

The seed creates one active course, one class, three Node runtimes
(`node-22`, `node-24`, `node-26`), one template/version, one test-suite/version,
and exactly one assignment: `assignment-01-string-utils`.
