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

## When a migration refuses to run

Two migrations add a constraint the application had always assumed and the
schema had never held. Where an older defect could have written data that
breaks it, the migration stops instead of deciding for you: what to keep is an
academic decision, and a migration that quietly deleted a submission or a score
would be worse than one that will not start. The application does not come up
until the data is resolved, so take the backup in step 2 first.

Both are no-ops on an instance that never hit the defect.

**`repository/commit pair(s) have more than one submission`** (V6). The same
commit was recorded against one repository twice, which the admission check
refuses and the schema had allowed. Find them:

```sql
SELECT repository_id, commit_sha, count(*), array_agg(id ORDER BY received_at)
FROM submissions GROUP BY repository_id, commit_sha HAVING count(*) > 1;
```

Keep the submission that was graded and that the student was shown - normally
the earliest - and delete the others by id, together with the grading runs that
hang from them.

**`grading run position(s) hold more than one result`** (V7). One grading run
holds two results for the same position, which means that run was written twice
and the student's result page has been listing every check twice. Find them:

```sql
SELECT grading_run_id, count(*) FROM test_results
GROUP BY grading_run_id, display_order HAVING count(*) > 1;
```

The duplicate sets come from two sandbox runs of the same submission and are
not required to agree, so check whether they do before choosing. If they agree,
deleting either set is safe. If they disagree, the run has no trustworthy score
and the honest repair is to delete every result for that run and regrade the
submission from the instructor interface.

