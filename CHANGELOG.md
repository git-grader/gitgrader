# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security

- Enforce LDAP transport security instead of only documenting it.
  `security.ldap.verify-certificate` and `security.ldap.referral` were declared and
  documented but never read, so an operator who enabled LDAP against the default
  `ldap://` URL sent instructor passwords and the manager bind credentials in plaintext
  while believing certificate verification was on. **Breaking:**
  `security.ldap.verify-certificate` is removed because it never had an effect, and a
  production instance now refuses to start unless `security.ldap.url` is `ldaps://`.
  Migration: move the directory to `ldaps://` before upgrading, and delete the removed
  property from your configuration.
- Scope SSH key revocation and replacement to the owning student. Both endpoints checked
  that the student named in the URL existed and then acted on whatever key identifier was
  supplied, so an instructor could revoke or replace another student's key through a
  mismatched path and lock them out of pushing.
- Record the signing key on accepted submissions. The ownership check resolved the
  fingerprint to a registered key and discarded the identifier, leaving
  `submissions.signature_key_id` null on every row; a fingerprint alone does not survive
  the key being deleted.
- Create `.env` readable only by its owner. It holds the database and directory
  passwords, and both `cp` and an editor leave it readable by every account on the host.
- Score a submission only against the tests its hidden manifest declares. The sandbox
  runs the reporter and the submission in one container, so both write to the same
  standard output, and every line matching TAP's status syntax was recorded as a test
  result. A submission that printed `ok 1 - anything` therefore minted passing tests and
  diluted the suite until the percentage said whatever it wanted. The manifest now
  decides which tests exist: one result per declared test, output naming anything else
  discarded, a test the output never reported recorded as not executed, and a test
  reported twice never counted as passed. A suite published without a manifest is
  refused as an infrastructure error rather than graded on output it cannot verify.
- Refuse a student number, course key or assignment key that could name a directory
  other than its own. A registration carrying `../` in its student number produced a
  repository path that normalised onto another student's bare repository: the two stored
  paths differed, so both satisfied the unique index, and each student then authenticated
  with their own key against a repository they shared.
- Reject a push that introduces more than 1000 new commits instead of silently
  truncating the walk at that number. Commits past the ceiling were never
  signature checked and were admitted anyway, so a large enough push could carry
  unsigned history in behind a signed tip.
- Refuse non-fast-forward pushes. JGit permits them unless
  `receive.denyNonFastForwards` is set, so a student could rewrite a branch and
  orphan commits that recorded submissions still reference, leaving a re-grade
  unable to find them.
- Enforce `git.max-push-size`, `git.max-file-size` and `git.max-file-count`,
  which were configurable and documented but applied nowhere.
- Enforce `security.rate-limits.ssh-auth-per-minute-per-ip` and
  `login-per-minute-per-ip`, which were likewise configurable and never
  consulted.
- Bound the SSH transport: authentication timeout, idle timeout, maximum
  authentication requests per connection, and maximum concurrent sessions.
- Publish a container image only from a semantic-version release tag, and move `:latest`
  only for a stable one. A manual run from a branch previously published that branch as
  an image tag and repointed `:latest` at it.

### Added

- Fair grading dispatch. A worker now claims at most one job per student and
  skips students who already occupy one, so a single student can no longer put
  an arbitrarily long queue in front of the rest of a course.
- Newest-wins coalescing. Only the most recent unstarted submission for a
  student and assignment is graded; older queued work is marked `CANCELLED`.
  Every push is still recorded, so attempt history is unchanged.
- Rolling hourly push allowances per assignment and per student, counted in the
  database so they hold across restarts and across instances.
- Rejection of a commit that was already submitted to the same repository.
- Queue ceilings per student, per course and per instance.
- `gitgrader.throttle` counter, tagged only with the limit and the decision, and
  a `RATE_LIMIT_TRIGGERED` audit record for every throttling decision.
- Initial self-hostable GitGrader release: Git-over-SSH submission admission,
  assignment grading, instructor and student web interfaces, and PostgreSQL
  persistence.
- Container deployment assets, operational documentation, CI workflows, and
  community policies.

### Changed

- The production profile no longer falls back to the standalone `gitgrader`/`gitgrader`
  database credentials. **Breaking:** `SPRING_DATASOURCE_USERNAME` and
  `SPRING_DATASOURCE_PASSWORD` must be set under the production profile; an instance
  without them now fails to start rather than reaching a real database with credentials
  anyone can guess. Compose already required both.
- Shutdown is now ordered and returns work rather than stranding it. The SSH
  endpoint stops accepting pushes first, the dispatcher then stops claiming,
  waits up to `grading.queue.drain-timeout` for a running sandbox, and hands
  back whatever is still executing. `spring.lifecycle.timeout-per-shutdown-phase`
  and the Compose `stop_grace_period` were raised above that window; previously
  Compose sent `SIGKILL` ten seconds after `SIGTERM`.
- Submission listings filter in the database. Every filter was previously applied to a
  page that had already been read, so matches on other pages were dropped and the
  reported total counted only the matches on the page in hand.
- The Compose project is named explicitly, so the volume names the grading sandbox is
  bind-mounted from no longer depend on what the checkout directory is called.
- Production bundles no longer carry source maps, which were 5.6 MB of the 7.4 MB the
  application served.

### Fixed

- `grading.max-parallel-jobs` bounds real concurrency. It claimed that many jobs and then
  ran them one at a time on the polling thread, so throughput was one container per
  instance while the surplus leases aged towards expiry before they could start.
- A grading worker whose lease expired or was reclaimed can no longer commit its result
  on top of whoever took the job over. Every job carries a lease generation, and starting,
  succeeding, and failing are conditional on still owning the current lease. Shutdown
  cancels an in-flight sandbox and waits for it to stop before handing the job back,
  instead of requeueing work that was still running.
- A submission's status cannot regress out of a terminal state under a concurrent or
  stale writer.
- Awarded points are computed from the pass ratio rather than from the already-rounded
  display percentage, which shifted the last cent on some point scales.
- A push carrying several refs is judged in full before any of it is recorded. One refused
  ref used to leave behind a submission and a result link for a ref that never landed, and
  the submission recorded `refs/heads/main` whichever branch the push actually updated.
- The instructor interface: publishing a runtime sent a report format the backend rejects,
  which blocked the only path to publishing anything; a published test suite version stayed
  invisible until a reload; deadlines were converted in the browser's timezone rather than
  the one the instructor chose; an unparseable date threw out of the submit handler and
  discarded the form; a cleared numeric field submitted zero; and error banners rendered
  empty for any failure that was not `problem+json`. API responses are now validated
  against their schemas at the boundary instead of being cast unchecked.
- A push is graded once. Spring Modulith replays an event publication it never saw
  marked complete, which is what a process dying between the listener's commit and that
  mark produces. The replay ran the whole queueing path again, so one push superseded the
  job its own first delivery had queued, took a second sandbox, and replaced the result
  the student had already been shown. A regrade is still a deliberate second attempt.
- A commit is recorded against a repository once, enforced by the schema and not only by
  the admission check. `submissions_unique_commit` included `received_at`, so the same
  commit pushed twice a second apart produced two distinct keys and the constraint
  accepted precisely the duplicate it is named for.
- Restore can verify the backup it is given. `scripts/backup.sh` recorded each checksum
  against the path it was told to write to, so a backup taken to the default `./backups`
  listed `./backups/gitgrader-.../postgresql.dump`, and the restore - which verifies from
  inside the backup directory - looked for that path beneath it and found nothing. Every
  such restore failed its own checksum check before touching anything.
- Backup and restore act on the volumes the application actually uses. Both derived the
  Compose project name from the checkout directory, but `compose.yaml` pins it and `-p`
  and `COMPOSE_PROJECT_NAME` override it. A checkout named anything else addressed
  volumes that did not exist, which Docker answers by creating them empty: backup wrote
  well-formed archives of nothing, and the loss surfaced only at restore. The name now
  comes from Compose, a missing volume fails the backup, and restore stops the
  application before emptying the volumes mounted into it.
- A shutdown during grading no longer consumes one of a submission's attempts.
  Three restarts while a run was in flight used to exhaust `max-attempts` and
  permanently mark the submission `INFRASTRUCTURE_ERROR`. An expired lease still
  consumes an attempt, which is what keeps a job that hangs its worker bounded.
- Requesting a regrade queues one. The endpoint answered `202 Accepted` and created no
  grading run, so a submission was never regraded.
- Course reports record the score a run actually earned. A submission's status was used
  as the score, so every attempt below its assignment's pass threshold was reported as
  zero points rather than the percentage it achieved.
- Editing an assignment or a course no longer moves its dates. The form seeded its
  date-time controls with UTC digits and read them back as local time, so saving an
  unchanged form shifted the deadline by the editor's offset, and each further save
  shifted it again.
- A failed request for the deployment's settings no longer renders a blank page, and an
  expired session returns to the sign-in page instead of an empty one.
- The assignment page reports that its templates, test suites or runtimes could not be
  loaded instead of showing a spinner that never resolves.
- Backups no longer archive the running PostgreSQL data directory, and restores no longer
  empty it underneath the server. The database is captured and restored through the
  logical dump alone.

[Unreleased]: https://github.com/git-grader/gitgrader/commits/main
