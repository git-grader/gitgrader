# GitGrader example assignments

This directory is an operator-owned source package. It is **not** a directory
to publish wholesale to students.

## Security boundary

Each `assignments/<assignment>/template/` directory is the complete project a
student clones. The visible files are, per assignment:

- `assignment-01-string-utils` (Node.js 24, TAP): `package.json` and
  `package-lock.json`, `.gitignore`, `.editorconfig`, `.nvmrc`, `README.md`,
  `src/string-utils.js`, `public-tests/string-utils.test.js` with its local
  `package.json`
- `assignment-02-text-tools` (Python 3.15, TAP): `README.md`, `.gitignore`,
  `pytest.ini`, `src/solution.py`, `public-tests/test_solution.py`
- `assignment-03-geo-utils` (Java 27, JUnit XML): `README.md`, `.gitignore`,
  `pom.xml`, `src/Solution.java`, `public-tests/src/test/java/SolutionTest.java`

The sibling `hidden-tests/` tree and the `reference-solution/` tree of every
assignment are operator-only artifacts. They must be stored separately from
template storage, never copied into a repository, and never served by a
student-facing endpoint. This is a hard security boundary: once a test input or
assertion reaches a student clone, it can no longer be used to independently
assess that student's submission.

At grading time, a shimmed round splits the suite and the submission across two
containers: the sandbox (S) container copies the submitted repository to
`/workspace` and starts the shim server; the suite (T) container mounts the
operator-owned test-suite read-only at `/opt/hidden-tests`. S never sees the
suite; only the shared shim socket at `/gitgrader-shim` connects them. The
grader joins the report's test names exactly to the operator manifest, then
presents only its sanitised categories and hints to students. Node and Python
report **TAP**; Java reports **JUnit XML** (see
`docs/grading-runtime-protocol.md`).

## Scoring

The score formula is `passed / total * 100`. All ten checks carry weight one,
so seven passing checks produce `7 / 10 * 100 = 70.0 %`.

The proof needs a Docker engine and the locally built runtime image. Build the
images once first; they are built rather than pulled, because each suite half
needs the tooling its runtime image installs (Jasmine, pytest, the Maven+JUnit
stack) and the stock base images do not carry it:

```sh
deployment/runtimes/build-runtimes.sh
```

Then run the proofs from the repository root:

```sh
./examples/verify-example.sh          # Node.js: assignment-01, node-24 image
./examples/verify-example-python.sh   # Python:  assignment-02, python-315 image
./examples/verify-example-java.sh     # Java:    assignment-03, java-27 image
```

Set `NODE_IMAGE`, `PYTHON_IMAGE`, or `JAVA_IMAGE` to grade with a different
runtime image, as long as it carries the same baked tooling.

Each script grades the complete implementation and the intentional 70%
implementation through the two-container shimmed path, checks report-to-manifest
names, and rejects any result other than 10/10 and 7/10 respectively.

## Scope

This example package verifies the three assignments under
`examples/assignments/` — one per language family (Node.js, Python, Java).
Other WBE assignment packages are separate course material and are not included
in this example, its seed data, or its verification scripts.

## Seed a running instance

First upload the template directory to the configured template store and the
operator test-suite directory to the configured test store. Update the storage
paths and content hashes in `seed-data.sql` if your storage layout differs.
Then load the idempotent records with a PostgreSQL client that targets the
GitGrader database:

```sh
psql "$DATABASE_URL" -f examples/seed-data.sql
```

The seed creates one active course, one class, five runtimes (`node-22`,
`node-24`, `node-26`, `python-315`, `java-27`), one template/version per
assignment, one test-suite/version per assignment, and one assignment per
language family (`assignment-01-string-utils` on node-24;
`assignment-02-text-tools` and `assignment-03-geo-utils` are seeded as
additional suites without assignment rows — wire them to a course by copying
the assignment insert with the matching template, suite and runtime ids).
