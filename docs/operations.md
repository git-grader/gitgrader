# Operations

## Health and observability

Use `/actuator/health/readiness` to decide whether the instance can receive
traffic. The default Compose health check calls this endpoint. Do not route
traffic to a process that has not become ready. Treat a process-level liveness
check separately from readiness: liveness answers whether a restart may help;
readiness answers whether dependencies required for requests are usable.

The application exposes Spring Boot Actuator endpoints subject to its security
configuration. Confirm the endpoint exposure policy in the deployed
configuration before scraping metrics or publishing health data. Keep metrics
and actuator endpoints on an administrator-only network where they reveal
operational information.

Logs are the primary incident record. Preserve the correlation ID supplied by
the application or edge proxy across HTTP requests, grading work, and support
records; never use a student name, key, result token, or source contents as a
correlation ID. Centralize structured logs and restrict access because error
context may contain assignment identifiers or controlled output.

Classify grading-related errors consistently:

1. **Student test failure** — a completed runner produced failing tests; record
   the result, do not page an operator.
2. **Invalid submission** — admission rejected a push or submission due to a
   domain rule; return the actionable validation reason.
3. **Runner error** — the isolated runner could not execute, timed out, or
   produced unusable output; retain technical diagnostics and retry only after
   assessing reproducibility.
4. **Infrastructure error** — database, Docker engine, storage, DNS, or network
   dependency failed; page the platform operator.
5. **Internal application error** — an unexpected application defect; preserve
   correlation ID and stack trace in protected logs, then open an incident.

## Runtimes and grading runs

Treat runtime definitions and images as controlled release artifacts. Review
their build source, pin image versions or recorded digests, test against a
representative assignment, and retain the exact runtime definition associated
with historical runs. Never replace a runtime image under an existing tag when
reproducibility matters.

Retry only runs whose failure was transient or caused by an operator-corrected
runner/infrastructure problem. Retrying a student test failure changes neither
the source nor expected result; retriggering after a template, test, or runtime
change should be recorded as a distinct grading decision. This documentation
does not assert a particular retry REST endpoint: use the deployed UI/API
contract to perform an available retry.

## Troubleshooting

| Symptom | Checks |
| --- | --- |
| App never becomes ready | `docker compose logs app database`; confirm the database health check, credentials, migration state, and writable mounted data directories. |
| SSH clone/push fails | Confirm TCP reachability to port 2222, advertised `git.ssh-host`/`git.ssh-port`, persisted host key, registered key type, and signature policy. |
| Grade remains pending | Inspect application and Docker-engine logs, runner image availability, mounted templates/tests, resource limits, and persisted event/job state. |
| Hidden tests appear in output | Stop sharing the affected output, rotate result tokens, inspect logs/artifacts/access controls, and follow the security response process. |
| Database is full or slow | Inspect PostgreSQL volume capacity, backup retention, artifact retention, long transactions, and worker concurrency before deleting data. |

Use `docker compose logs --since=30m app database`, `scripts/verify-install.sh`,
host escape, hidden-test disclosure, token leak, or credential exposure through
the private process in [SECURITY.md](../SECURITY.md).
