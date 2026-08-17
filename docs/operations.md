# Operations

## Health and observability

Use `/actuator/health/readiness` to decide whether the instance can receive
traffic. The Compose file carries no in-container health check, because the
buildpack run image has no shell to run one with; probe the endpoint from
outside the container instead. Do not route
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

## Shutdown and restart

Stopping the application is ordered, and the order is the point. The SSH
endpoint stops first, so no push is admitted into a queue that is about to
drain; the grading dispatcher then stops claiming, waits up to
`grading.queue.drain-timeout` for a sandbox already running to finish, and hands
back anything still executing. A returned job goes to `PENDING` with the attempt
it consumed refunded, because a redeploy is the platform's doing and must not
count against a student. A lease that expires without an orderly shutdown is
**not** refunded: that job may be one that hangs its worker, and it has to stay
bounded.

Three timeouts have to stay ordered, each one strictly larger than the one above
it, and they live in three different files:

| Setting | Default | Where |
| --- | --- | --- |
| `grading.queue.drain-timeout` | `30s` | `application.yaml` |
| `spring.lifecycle.timeout-per-shutdown-phase` | `60s` | `application.yaml` |
| `stop_grace_period` | `90s` | `compose.yaml` |

Raising the drain window without raising the two below it means the process is
killed part-way through handing work back. Compose sends `SIGKILL` ten seconds
after `SIGTERM` unless `stop_grace_period` says otherwise, which is far less
than a grading run needs, so the value is set explicitly rather than left to the
default. If you run under Kubernetes or systemd instead, set the equivalent
termination grace period above the lifecycle timeout.

Nothing is lost if the process is killed outright. Submissions and jobs are
rows, the event registry replays unfinished publications on restart, and a job
whose lease expires is returned to the queue by the reaper — it just takes until
`grading.queue.claim-timeout` instead of happening immediately, and the student
sees the submission sit in `RUNNING` for that long.

## Troubleshooting

| Symptom | Checks |
| --- | --- |
| App never becomes ready | `docker compose logs app database`; confirm the database health check, credentials, migration state, and writable mounted data directories. |
| SSH clone/push fails | Confirm TCP reachability to port 2222, advertised `git.ssh-host`/`git.ssh-port`, persisted host key, registered key type, and signature policy. |
| Grade remains pending | Inspect application and Docker-engine logs, runner image availability, mounted templates/tests, resource limits, and persisted event/job state. |
| Submission says it was superseded | Expected when a student pushed again before the earlier run started: only the newest unstarted submission for an assignment is graded. The earlier attempt is still recorded. |
| Student reports a push was refused | Check the audit trail for `RATE_LIMIT_TRIGGERED` against that student; the entry names the limit and the decision. Duplicate commits, the rolling hourly allowances, and the queue ceilings all refuse with an explanation on the Git side band. |
| Hidden tests appear in output | Stop sharing the affected output, rotate result tokens, inspect logs/artifacts/access controls, and follow the security response process. |
| Database is full or slow | Inspect PostgreSQL volume capacity, backup retention, artifact retention, long transactions, and worker concurrency before deleting data. |

Use `docker compose logs --since=30m app database`, `scripts/verify-install.sh`,
host escape, hidden-test disclosure, token leak, or credential exposure through
the private process in [SECURITY.md](../SECURITY.md).

## Sizing the grading workers

`GRADING_MAX_PARALLEL_JOBS` defaults to 2, which is a safe default and not a
capacity plan. It bounds how many sandboxes one instance runs at once, so the
queue drains at roughly:

```
jobs per hour = workers × 3600 / average grading seconds
```

A class of 300 submitting in the hour before a deadline, each run averaging two
minutes, needs ten workers to clear within that hour; two workers would take most
of a day. Size it from a measured average rather than from the timeout, and give
the host the CPU, memory and disk to match — raising it past what the machine can
run turns queue latency into thrashing.

Each worker holds a database connection only between runs, never during one, but
its start and finish do compete with HTTP and SSH for the pool. Raise
`DB_POOL_SIZE` alongside the worker count, and keep headroom for the web tier.

Two instances sharing one database is also a valid way to add capacity: work is
claimed with `SELECT ... FOR UPDATE SKIP LOCKED` and every claim carries a lease
generation, so a worker that loses its lease cannot write the result it was
computing.
