# Upgrade

**Back up first.** Do not upgrade without a tested PostgreSQL-and-volume backup.
Flyway migrations are forward-only: the application validates migrations at
startup and never uses Hibernate schema creation. Do not edit an applied
migration.

1. Record pre-upgrade counts and image version/digest:

   ```sql
   SELECT 'students', count(*) FROM students UNION ALL SELECT 'ssh_keys', count(*) FROM ssh_keys
   UNION ALL SELECT 'assignments', count(*) FROM assignments UNION ALL SELECT 'submissions', count(*) FROM submissions
   UNION ALL SELECT 'grading_runs', count(*) FROM grading_runs UNION ALL SELECT 'result_tokens', count(*) FROM result_tokens;
   ```

2. Create a backup: `scripts/backup.sh ./backups`, and archive `config/`,
   `deployment/runtimes/`, Compose files, and protected `.env`.
3. Read release notes, update `GITGRADER_VERSION`, pull the target image, and
   run `docker compose up -d`. Monitor migrations and readiness.
4. Run `scripts/verify-install.sh`, then repeat the SQL above. Counts must be
   equal or explainable by intentional concurrent activity. Sample users, key
   history, assignments, repositories, submissions, grading results, and result
   token behavior before declaring success.

An application-image rollback may be safe only if the target database migrations
remain compatible with the previous version. There is no general Flyway
downgrade. For a failed irreversible migration, stop the stack, restore the
pre-upgrade database and all volumes using [backup/restore](backup-restore.md),
then run the previous image. Practice this path before an urgent incident.
