# Contributing

Contributions are accepted under the Apache-2.0 license. Read the
[Code of Conduct](CODE_OF_CONDUCT.md) and do not include student work, hidden
tests, credentials, private keys, result tokens, or production data in issues,
commits, or pull requests.

## Local workflow

```sh
./mvnw -Plicense clean verify
./mvnw spring-javaformat:apply
./mvnw spring-boot:run
```

`-Plicense clean verify` is the same gate CI runs. The `license` profile
validates SPDX headers (the most common first PR failure), formatting,
Checkstyle, PMD/CPD, SpotBugs with FindSecBugs, forbidden-apis, JaCoCo,
frontend lint/test/build, Spring Modulith verification, and ArchUnit tests.
A plain `verify` skips the license profile, so it can pass locally and fail on
a missing header in CI.

Integration tests (`*IT` / `*IntegrationTest`) need Docker/Testcontainers.
Without Docker they are skipped rather than failed, so the command still
completes. When `CI` is set the same tests run regardless and a missing engine
fails the build.

> **JaCoCo and IDE recompilation.** m2e derives its class output from
> Maven's build directory, so an editor that recompiles after the tests have
> already executed rewrites `.class` files and invalidates JaCoCo's execution
> data. `jacoco-check` then fails with zero coverage even though every test
> passed. Close the editor and re-run when that happens.

For frontend-only iteration, run `npm ci`, `npm run dev`, `npm run lint`,
`npm run test:ci`, or `npm run build` in `frontend/`; Maven's frontend profile
uses the same scripts. Use `-DskipFrontend=true` only when intentionally
excluding frontend work, and never `-DskipQuality` for a pull request. The
optional native build is `./mvnw -Pnative native:compile`.

## Quality gate

`spring-javaformat` owns Java layout; apply it, never hand-format. The JaCoCo
floor is 55% line and 45% branch for the unit tests alone; with `CI` set the
integration tests are guaranteed to have run and the floor is 75% line and 60%
branch, so a change that passes locally without Docker can still be short in
CI. Add the SPDX header to every new file. The `security` profile runs OWASP
Dependency-Check and fails at CVSS 7 or above; set an NVD API key in CI for
reliable scans:

```sh
./mvnw -Psecurity verify
```

Spring Modulith's `ApplicationModules.verify()` and the Taikai/ArchUnit tests
enforce module boundaries. A module may depend only on packages declared in its
`package-info.java`; do not fix a violation with a cross-module repository or
service call — use the published application event/API boundary instead.

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

Add database changes as a new, forward-only Flyway migration; never edit an
applied one. Review the module's `package-info.java` before adding an import
outside its boundary — the architecture test intentionally makes accidental
coupling a build failure. See [docs/development.md](docs/development.md) for the
full Flyway and module-boundary guidance.

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

- run `./mvnw -Plicense clean verify`;
- update tests, migrations, API docs, and operational documentation as needed;
- describe security, schema, deployment, and rollback implications;
- confirm no secret, hidden test, or identifiable student data is included;
- ensure commits have DCO sign-offs.
