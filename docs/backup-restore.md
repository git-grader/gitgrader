# Backup and restore

Back up before every upgrade and test restores regularly. A database dump alone
is insufficient: Git repositories, templates, hidden test suites, and retained
artifacts live in volumes of their own, and a result cannot be reproduced without
the runtime images that produced it.

## What to back up

- PostgreSQL, including Spring Modulith event-publication state.
- Git repositories (`git-data`).
- Grading work data (`grading-data`) and retained artifacts (`artifacts`).
- Template content (`templates`) and hidden test suites (`tests`).
- Runtime definitions under `deployment/runtimes/` and any external runtime
  images or registry references needed to reproduce a run.
- `compose.yaml`, `.env` (stored securely), mounted `config/`, reverse-proxy
  configuration, and the application image version/digest.

`scripts/backup.sh` writes a PostgreSQL custom-format dump plus compressed
archives of every named Compose volume except the database's own, and SHA-256
checksums. PostgreSQL is captured by the dump alone: a tar of a data directory
that a running server is writing to is torn across checkpoints and cannot be
restored from. The script does not copy files outside named volumes; archive the
configuration and runtime definitions separately with the same protected backup
system.

## Create a backup

From the Compose project directory, with the database running:

```sh
POSTGRES_DB=gitgrader POSTGRES_USER=gitgrader scripts/backup.sh ./backups
tar -czf ./backups/config-and-runtimes-$(date -u +%Y%m%dT%H%M%SZ).tar.gz \
  config deployment/runtimes compose.yaml .env
```

The script uses:

```sh
docker compose exec -T database pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc
```

and mounts each named volume read-only into a short-lived Alpine container
to create a tar archive. Store the resulting directory encrypted and off-host.

## Restore drill

Run a restore first on an isolated host or Compose project. This procedure
destroys the current database and volume contents.

1. Verify the backup checksums and unpack the separately archived configuration
   and runtime definitions. Review `.env`; use restored secrets only in a
   protected environment.
2. Restore the application data. The script stops the application, starts the
   database, checks `SHA256SUMS`, empties and extracts the five non-database
   volumes, recreates the PostgreSQL database, then executes
   `pg_restore --clean --if-exists`:

   ```sh
   POSTGRES_DB=gitgrader POSTGRES_USER=gitgrader \
     scripts/restore.sh ./backups/gitgrader-YYYYMMDDTHHMMSSZ
   ```

3. Start the exact application image version used for the backup, then verify
   health and service reachability:

   ```sh
   docker compose up -d app
   scripts/verify-install.sh
   ```

4. Compare database counts with the source environment, inspect a representative
   assignment, registered key, repository, template, hidden suite, submission,
   and grading result. Run a non-destructive sample grading only if course
   policy permits it.

## Verification criteria

A restore is verified only when checksums pass, PostgreSQL starts cleanly, the
application reports readiness, the SSH port accepts TCP connections, expected
row counts match, Git repositories can be read, and a representative historical
result still resolves with its artifacts. Record the application image digest,
backup timestamp, operator, duration, count comparison, and any discrepancy.

Use `scripts/backup.sh --help` and `scripts/restore.sh --help` for the exact
arguments. Both scripts require Bash, Docker Compose, `pg_dump` inside the
database image, `tar`, `sha256sum`, and `realpath` on the host.
