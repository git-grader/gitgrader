# Development

## Prerequisites

Use JDK 25 and Maven Wrapper. The build requires Maven 3.9 or newer and
downloads Node v24.13.0/npm 11.8.0 through `frontend-maven-plugin`; Docker is
required for Testcontainers integration tests and for container-image builds.

```sh
./mvnw clean verify
./mvnw spring-boot:run
./mvnw spring-boot:build-image
```

The frontend is Vite/React/TypeScript and is built into the Boot application.
For standalone frontend work:

```sh
cd frontend
npm ci
npm run dev
```

Two TypeScript versions are installed on purpose. Type checking runs on
TypeScript 7 through `npm run typecheck`, which the build calls before Vite.
`typescript-eslint` cannot load against 7 yet, so the package named
`typescript` stays on 6 to satisfy it and the 7 compiler is installed
alongside as `typescript7`. Once `typescript-eslint` supports 7, drop the
alias and point `typescript` at it.

Use the development Compose overlay for a source-built environment:

```sh
cp .env.example .env
scripts/dev-up.sh
scripts/dev-down.sh
```

## Tests and quality gate

`./mvnw clean verify` runs the required checks. Integration tests named `*IT`
or `*IntegrationTest` run through Failsafe and need Docker/Testcontainers. The
full quality gate includes spring-javaformat, Checkstyle, PMD/CPD, SpotBugs with
FindSecBugs, forbidden-apis, JaCoCo, frontend lint/test/build, Spring Modulith
verification, and Taikai/ArchUnit tests. Use `./mvnw spring-javaformat:apply`
before committing Java changes.

The experimental native executable attempt is `./mvnw -Pnative native:compile`.

## Flyway changes

Create the next ordered migration in
`backend/src/main/resources/db/migration/`, for example
`V2__add_assignment_visibility.sql`. Migrations are forward-only: never alter
or delete one once any environment has applied it. Test against an existing
database state, include the corresponding model/tests, and document operational
impact. A rollback is a new corrective migration or restoration of a pre-upgrade
backup, not editing migration history.

## Module boundaries

The root packages are Spring Modulith application modules. Each module’s
`package-info.java` declares which other modules it may use and why. The
`ApplicationModules.verify()` test rejects an undeclared dependency. Keep
domain state and repositories within their owning module; communicate across a
cycle through the documented module API or persistent application event. Do not
make an architectural test pass by widening an allow-list without a clear
domain reason.
