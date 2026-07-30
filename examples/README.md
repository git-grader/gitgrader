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

At grading time, the runner copies the submitted repository to `/workspace`;
it then mounts the operator-owned test-suite read-only at `/opt/hidden-tests`.
The mount is deliberately outside `/workspace`: a student path traversal cannot
discover the suite through the repository tree. The runner provides
`SOLUTION_PATH=/workspace/src/string-utils.js` and executes:

```sh
node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js
```

GitGrader standardises this example on Node's **TAP** reporter. The grader joins
the TAP subtest names exactly to the operator manifest, then presents only its
sanitised categories and hints to students.

## Scoring

The score formula is `passed / total * 100`. All ten checks carry weight one,
so seven passing checks produce `7 / 10 * 100 = 70.0 %`. Run the proof from the
repository root:

```sh
export PATH="$HOME/.nvm/versions/node/v22.23.1/bin:$PATH"
./examples/verify-example.sh
```

The script runs the operator suite against the complete implementation and the
intentional 70% implementation, checks TAP-to-manifest names, and rejects any
result other than 10/10 and 7/10 respectively.

## Add an assignment

1. Copy `assignments/assignment-01-string-utils/` to a new key.
2. Edit only the copied `template/` for student-visible instructions and public
   smoke tests.
3. Keep the copied suite and manifest outside template storage; give every test
   a stable ID, a coarse category, and a non-revealing hint.
4. Add a reference implementation and an executable proof script or extend
   `verify-example.sh` to demonstrate expected scoring.
5. Publish immutable template and test-suite versions, then make the assignment
   non-draft only after linking both versions and a runtime.

## Seed a running instance

First upload the template directory to the configured template store and the
operator test-suite directory to the configured test store. Update the storage
paths and content hashes in `seed-data.sql` if your storage layout differs.
Then load the idempotent records with a PostgreSQL client that targets the
GitGrader database:

```sh
psql "$DATABASE_URL" -f examples/seed-data.sql
```

The seed creates one active course, one class, one `node-24` runtime, one
template/version, one test-suite/version, and twelve realistic assignments.
Only assignment 01 maps to the files in this package; its eleven siblings make
the course report useful while reusing the same immutable sample artifacts.
