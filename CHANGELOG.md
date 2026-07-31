# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security

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

### Changed

- Shutdown is now ordered and returns work rather than stranding it. The SSH
  endpoint stops accepting pushes first, the dispatcher then stops claiming,
  waits up to `grading.queue.drain-timeout` for a running sandbox, and hands
  back whatever is still executing. `spring.lifecycle.timeout-per-shutdown-phase`
  and the Compose `stop_grace_period` were raised above that window; previously
  Compose sent `SIGKILL` ten seconds after `SIGTERM`.

### Fixed

- A shutdown during grading no longer consumes one of a submission's attempts.
  Three restarts while a run was in flight used to exhaust `max-attempts` and
  permanently mark the submission `INFRASTRUCTURE_ERROR`. An expired lease still
  consumes an attempt, which is what keeps a job that hangs its worker bounded.

## [0.1.0] - 2026-07-29

### Added

- Initial self-hostable GitGrader release: Git-over-SSH submission admission,
  assignment grading, instructor and student web interfaces, and PostgreSQL
  persistence.
- Container deployment assets, operational documentation, CI workflows, and
  community policies.

[Unreleased]: https://github.com/git-grader/gitgrader/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/git-grader/gitgrader/releases/tag/v0.1.0
