# GitGrader example assignments

This directory is an operator-owned source package. It is **not** a directory
to publish wholesale to students.

## Security boundary

Each `assignments/<name>/template/` directory is the complete project a student
clones. For `assignment-01-string-utils` its visible files are:

- `package.json` and `package-lock.json`
- `.gitignore`, `.editorconfig`, and `.nvmrc`
- `README.md`
- `src/string-utils.js`
- `public-tests/string-utils.test.js` and its local `package.json`

The Java and Python examples expose the equivalent starter: `src/Solution.java`
plus `public-tests/PublicSmoke.java`, and `src/solution.py` plus
`public-tests/public_smoke.py`, each with a `README.md` and `.gitignore`.

The sibling `hidden-tests/` tree and the `reference-solution/` tree of every
assignment are operator-only artifacts. They must be stored separately from
template storage, never copied into a repository, and never served by a
student-facing endpoint. This is a hard security boundary: once a test input or
assertion reaches a student clone, it can no longer be used to independently
assess that student's submission.

At grading time, a shimmed round splits the suite and the submission across two
containers: the sandbox (S) container copies the submitted repository to
`/workspace` and starts the runtime's shim server; the suite (T) container
mounts the operator-owned test-suite read-only at `/opt/hidden-tests`. S never
sees the suite; only the shared shim socket at `/gitgrader-shim` connects them.
All three runtimes standardise on **TAP**: the Node suite uses the jasmine TAP
reporter, the Java suite runs the baked `TapRunner` (the JUnit console launcher
has no TAP flag), and the Python suite runs the baked `tap_runner.py`
(pytest-tap's `--tap-stream` omits the `- ` separator the parser requires). The
grader joins the TAP subtest names exactly to the operator manifest, then
presents only its sanitised categories and hints to students.

## Scoring

The score formula is `passed / total * 100`. All ten checks carry weight one,
so seven passing checks produce `7 / 10 * 100 = 70.0 %`.

The proof needs a Docker engine, a host `node`, and the locally built runtime
images. Build the images once first; they are built rather than pulled, because
the suite halves need the tooling the runtime images install (jasmine, pytest,
the compiled Java shim and the JUnit console jar) and the stock base images do
not carry:

```sh
deployment/runtimes/build-runtimes.sh
```

Then run the proof from the repository root:

```sh
./examples/verify-example.sh
```

Set `NODE_IMAGE`, `PYTHON_IMAGE`, or `JAVA_IMAGE` to grade a leg with a
different runtime image, as long as it carries the same baked tooling.

The script grades the complete implementation and the intentional 70%
implementation of all three language examples through the two-container shimmed
path, checks TAP-to-manifest names, and rejects any result other than 10/10 and
7/10 respectively.

## Scope

This example package intentionally contains and verifies only the three
string-utils assignments under `examples/assignments/`:
`assignment-01-string-utils` (Node), `assignment-02-string-utils-java`, and
`assignment-03-string-utils-python`. They are the only directories under
`examples/assignments/`. Other WBE assignment packages are separate course
material and are not included in this example, its seed data, or its
verification script.

## Seed a running instance

First upload the template directories to the configured template store and the
operator test-suite directories to the configured test store. Update the storage
paths and content hashes in `seed-data.sql` if your storage layout differs.
Then load the idempotent records with a PostgreSQL client that targets the
GitGrader database:

```sh
psql "$DATABASE_URL" -f examples/seed-data.sql
```

The seed creates one active course, one class, the Node runtimes (`node-22`,
`node-24`, `node-26`) and the Python runtime (`python-3.14`), one
template/version and test-suite/version per string-utils example, and the
assignments `assignment-01-string-utils` and `assignment-03-string-utils-python`.
The Java assignment row is commented out until a `java-25`/`java-27` runtime is
registered, because `assignments.runtime_id` requires the built image's
registry digest; see the note at the bottom of `seed-data.sql`.
