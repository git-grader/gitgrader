# Contributing

Contributions are accepted under the Apache-2.0 license. Read the
[Code of Conduct](CODE_OF_CONDUCT.md) and do not include student work, hidden
tests, credentials, private keys, result tokens, or production data in issues,
commits, or pull requests.

## Local workflow

Install JDK 25 and use the Maven wrapper. The build installs Node v24.18.1 and
npm 11.16.0 into `backend/target/frontend-toolchain`; a separately installed
Node is useful only for frontend-only work.

```sh
./mvnw clean verify
./mvnw spring-javaformat:apply
./mvnw spring-boot:run
./mvnw spring-boot:build-image
```

Run `./mvnw clean verify` before opening a pull request. It runs formatting
validation, Checkstyle, frontend lint/test/build (unless skipped), unit and
integration tests, PMD and CPD, SpotBugs with FindSecBugs, forbidden-apis,
JaCoCo, and module architecture tests. Testcontainers integration tests require
a reachable Docker engine. The optional native build is:

```sh
./mvnw -Pnative native:compile
```

For local frontend iteration, run `npm ci`, `npm run dev`, `npm run lint`,
`npm run test:ci`, or `npm run build` in `frontend/`. Maven's frontend profile
uses the same scripts. Use `-DskipFrontend=true` only when intentionally
excluding frontend work; do not use `-DskipQuality` for a pull request.

## Quality gate

`spring-javaformat` owns Java layout. Apply it rather than hand-formatting:

```sh
./mvnw spring-javaformat:apply
```

Checkstyle runs during `validate` for production and test Java. PMD and CPD run
at `verify` (CPD minimum token count 120), as do SpotBugs/FindSecBugs and
forbidden-apis. JaCoCo requires 50% bundle line and 40% branch coverage. The
`security` profile runs OWASP Dependency-Check and fails at CVSS 7 or above;
set an NVD API key in CI for reliable scans:

```sh
./mvnw -Psecurity verify
```

Spring Modulith’s `ApplicationModules.verify()` and the Taikai/ArchUnit tests
enforce module boundaries. A module may depend only on packages declared in its
`package-info.java`; do not fix a violation with a cross-module repository or
service call. Use the published application event/API boundary instead.

## Rules that commonly surprise contributors

The forbidden-apis configuration rejects:

- `Instant.now()` and related clock reads; inject `Clock`.
- `String.format` without an explicit `Locale`, as well as locale-default case
  and number formatting APIs; use `Locale.ROOT` for machine-readable text.
- Default-charset APIs such as `String#getBytes()`, reader/writer constructors,
  and `ByteArrayOutputStream#toString()`; specify a `Charset`, normally UTF-8.
- `System.out` and `System.err`; use the project logger.
- `Runtime.exec`, `ProcessBuilder`, `Math.random`, and no-argument `Random`.

The build also bans hard-coded public URLs and `Runtime.exec`/`ProcessBuilder`
through PMD. Keep public endpoints in typed configuration and run untrusted code
only through the grading runner abstraction.

## Database and modules

Add database changes as a new, forward-only Flyway migration in
`backend/src/main/resources/db/migration/`; never edit an applied migration.
Add the entity/model, migration, tests, and documentation in the same change.
Review the module’s `package-info.java` before adding an import outside its
boundary. The architecture test intentionally makes accidental coupling a build
failure.

## Commits and pull requests

Use short imperative subjects with a scope when useful, for example
`grading: enforce runner timeout`. Keep unrelated formatting or refactors out
of functional changes. Sign every commit with the Developer Certificate of
Origin:

```sh
git commit -s -m "grading: enforce runner timeout"
```

By signing off, you certify the DCO statement:

> Signed-off-by: Your Name <your.email@example.org>

Before requesting review:

- run `./mvnw clean verify`;
- update tests, migrations, API docs, and operational documentation as needed;
- describe security, schema, deployment, and rollback implications;
- confirm no secret, hidden test, or identifiable student data is included;
- ensure commits have DCO sign-offs.
