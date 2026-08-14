# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security

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
