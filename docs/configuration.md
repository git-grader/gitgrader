# Configuration reference

Environment variables override external `/config/application.yaml`, which
overrides packaged defaults. The product properties below are exhaustive for
`app`, `git`, `grading`, `storage`, `security`, and `audit`; framework keys are
listed in the second table.

| YAML path | Environment variable | Default | Purpose |
| --- | --- | --- | --- |
| `app.name` | `APP_NAME` | `GitGrader` | Application name. |
| `app.public-url` | `APP_PUBLIC_URL` | `http://localhost:8080` | Public HTTP base URL. |
| `app.support-email` | `APP_SUPPORT_EMAIL` | `support@example.org` | Support address. |
| `app.organization-name` | `APP_ORGANIZATION_NAME` | `Example Organization` | Displayed organization label. |
| `app.documentation-url` | `APP_DOCUMENTATION_URL` | GitHub project URL | Documentation link. |
| `app.default-timezone` | `APP_DEFAULT_TIMEZONE` | `UTC` | Default zone. |
| `app.data-directory` | `APP_DATA_DIRECTORY` | `/data` | General data root. |
| `app.registration.enabled` | `APP_REGISTRATION_ENABLED` | `true` | Allow student registration. |
| `app.registration.require-instructor-verification` | `APP_REQUIRE_INSTRUCTOR_VERIFICATION` | `false` | Require instructor verification. |
| `app.registration.max-keys-per-student` | `APP_MAX_KEYS_PER_STUDENT` | `5` | Registered-key cap. |
| `app.result-tokens.entropy-bits` | `APP_RESULT_TOKEN_ENTROPY_BITS` | `256` | Token entropy; minimum 128. |
| `app.result-tokens.time-to-live` | `APP_RESULT_TOKEN_TTL` | `P180D` | Token lifetime. |
| `app.result-tokens.prefix-length` | — | `8` | Stored support-prefix length. |
| `git.enabled` | `GIT_ENABLED` | `true` | Start SSH endpoint. |
| `git.ssh-host` / `git.ssh-port` | `GIT_SSH_HOST` / `GIT_SSH_PORT` | `localhost` / `2222` | Advertised clone endpoint. |
| `git.listen-address` / `git.listen-port` | `GIT_LISTEN_ADDRESS` / `GIT_LISTEN_PORT` | `0.0.0.0` / `2222` | SSH bind endpoint. |
| `git.ssh-user` | `GIT_SSH_USER` | `git` | Fixed clone user. |
| `git.host-key-path` | `GIT_HOST_KEY_PATH` | `/data/git/ssh/hostkey.ser` | Persistent SSH host key. |
| `git.repository-directory` | `GIT_REPOSITORY_DIRECTORY` | `/data/git/repositories` | Bare repository root. |
| `git.max-push-size` / `git.max-file-size` / `git.max-file-count` | `GIT_MAX_PUSH_SIZE` / `GIT_MAX_FILE_SIZE` / `GIT_MAX_FILE_COUNT` | `50MB` / `10MB` / `2000` | Push admission limits. |
| `git.require-signed-commits` | `GIT_REQUIRE_SIGNED_COMMITS` | `true` | Reject unsigned commits. |
| `git.idle-timeout` | — | `10m` | SSH idle timeout. |
| `git.allowed-key-types` | — | packaged list | Accepted OpenSSH key blob types. |
| `grading.runner` | `GRADING_RUNNER` | `docker` | Runner implementation selector. |
| `grading.working-directory` | `GRADING_WORKING_DIRECTORY` | `/data/grading` | Runner workspace root. |
| `grading.max-parallel-jobs` | `GRADING_MAX_PARALLEL_JOBS` | `2` | Worker concurrency. |
| `grading.default-timeout` | `GRADING_DEFAULT_TIMEOUT` | `120s` | Run time limit. |
| `grading.default-memory-limit` / `default-cpu-limit` / `default-pid-limit` | `GRADING_DEFAULT_MEMORY_LIMIT` / `GRADING_DEFAULT_CPU_LIMIT` / `GRADING_DEFAULT_PID_LIMIT` | `512MB` / `1.0` / `256` | Default resource limits. |
| `grading.network-enabled` | `GRADING_NETWORK_ENABLED` | `false` | Permit runner network access. |
| `grading.log-size-limit` | `GRADING_LOG_SIZE_LIMIT` | `1MB` | Captured log limit. |
| `grading.synchronous-timeout` | `GRADING_SYNCHRONOUS_TIMEOUT` | `20s` | Synchronous wait bound. |
| `grading.retain-workspaces` | `GRADING_RETAIN_WORKSPACES` | `false` | Retain workspaces for diagnosis. |
| `grading.docker.host` | `GRADING_DOCKER_HOST` | `unix:///var/run/docker.sock` | Docker API endpoint. |
| `grading.docker.workspace-mount-root` | `GRADING_DOCKER_WORKSPACE_MOUNT_ROOT` | empty | Host-visible workspace root. |
| `grading.docker.tests-mount-root` | `GRADING_DOCKER_TESTS_MOUNT_ROOT` | empty | Host-visible hidden-test root. Required whenever the application itself runs in a container: the Docker daemon resolves sandbox mounts on the host, so leaving this empty passes the application's own path through, the daemon creates it empty rather than failing, and every submission is graded against no tests and scored zero. |
| `grading.docker.user` | `GRADING_DOCKER_USER` | `65534:65534` | Sandbox UID:GID. |
| `grading.docker.pull-timeout` | — | `5m` | Image-pull timeout. |
| `grading.docker.read-only-root-filesystem` / `tmpfs-size` / `drop-all-capabilities` / `no-new-privileges` | — | `true` / `64MB` / `true` / `true` | Docker hardening defaults. |
| `grading.queue.poll-interval` / `claim-timeout` / `max-attempts` / `retry-backoff` | — | `2s` / `15m` / `3` / `30s` | Database queue operation. |
| `grading.queue.max-pending-per-student-per-course` / `max-pending-per-course` / `max-pending-global` | `GRADING_QUEUE_MAX_PENDING_STUDENT` / `GRADING_QUEUE_MAX_PENDING_COURSE` / `GRADING_QUEUE_MAX_PENDING_GLOBAL` | `3` / `500` / `1000` | Ceilings on unstarted grading work. Coalescing already keeps a student to one queued run per assignment, so these are safety nets. |
| `grading.queue.drain-timeout` | `GRADING_QUEUE_DRAIN_TIMEOUT` | `30s` | How long shutdown waits for a running sandbox before returning its job to the queue. Must stay below `spring.lifecycle.timeout-per-shutdown-phase`, which must stay below the container runtime's stop grace period. |
| `storage.repositories-directory` / `templates-directory` / `tests-directory` / `artifacts-directory` / `temp-directory` | `STORAGE_REPOSITORIES_DIRECTORY` / `STORAGE_TEMPLATES_DIRECTORY` / `STORAGE_TESTS_DIRECTORY` / `STORAGE_ARTIFACTS_DIRECTORY` / `STORAGE_TEMP_DIRECTORY` | `/data/git/repositories` / `/data/templates` / `/data/tests` / `/data/artifacts` / `/data/tmp` | Persistent data locations. |
| `security.ldap.enabled` | `SECURITY_LDAP_ENABLED` | `false` | Authenticate instructors and administrators against a directory. With this off and local accounts off, nothing can sign in. |
| `security.ldap.url` | `SECURITY_LDAP_URL` | `ldap://localhost:389` | Directory endpoint. Use `ldaps://`, or StartTLS, for anything but a throwaway. |
| `security.ldap.base-dn` | `SECURITY_LDAP_BASE_DN` | empty | Root the searches below are relative to. |
| `security.ldap.manager-dn` / `manager-password` | `SECURITY_LDAP_MANAGER_DN` / `SECURITY_LDAP_MANAGER_PASSWORD` | empty / empty | Account used to search the directory. Treat the password as a secret; give the account read access and nothing more. |
| `security.ldap.user-search-base` / `user-search-filter` | `SECURITY_LDAP_USER_SEARCH_BASE` / `SECURITY_LDAP_USER_SEARCH_FILTER` | `ou=people` / `(uid={0})` | Where and how a sign-in name is resolved; `{0}` is the submitted name. |
| `security.ldap.group-search-base` / `group-search-filter` | `SECURITY_LDAP_GROUP_SEARCH_BASE` / `SECURITY_LDAP_GROUP_SEARCH_FILTER` | `ou=groups` / `(member={0})` | Where and how the groups of a resolved user are found; `{0}` is their DN. |
| `security.ldap.instructor-group` / `admin-group` | `SECURITY_LDAP_INSTRUCTOR_GROUP` / `SECURITY_LDAP_ADMIN_GROUP` | `gitgrader-instructors` / `gitgrader-admins` | Group names granting each role. Membership of neither means a valid directory account still cannot sign in. |
| `security.ldap.verify-certificate` | `SECURITY_LDAP_VERIFY_CERTIFICATE` | `true` | Validate the directory's TLS certificate. Turning this off makes the encrypted connection impersonatable, which costs the credentials that cross it. |
| `security.ldap.referral` | — | `follow` | How directory referrals are handled. |
| `security.local-accounts.enabled` | `SECURITY_LOCAL_ACCOUNTS_ENABLED` | `false` | Development accounts; forbidden in production. |
| `security.rate-limits.*` | `SECURITY_RATE_REGISTRATION_IP`, `SECURITY_RATE_REGISTRATION_GLOBAL`, `SECURITY_RATE_RESULT_IP`, `SECURITY_RATE_LOGIN_IP`, `SECURITY_RATE_SSH_IP` | `5`, `200`, `60`, `10`, `30` | Per-address abuse limits, held in memory. They reset on restart and are counted per instance. Block duration is `15m`. |
| `security.rate-limits.submissions-per-hour-per-assignment` / `submissions-per-hour-per-student` | `SECURITY_RATE_SUBMISSIONS_ASSIGNMENT` / `SECURITY_RATE_SUBMISSIONS_STUDENT` | `20` / `60` | Rolling hourly push allowances. Counted in the database, so unlike the limits above they survive a restart and hold across instances. Exceeding one refuses the push with an explanation on the Git side band. |
| `security.session.timeout` / `cookie-name` / `secure-cookie` / `same-site` | `SECURITY_SESSION_TIMEOUT` / `SECURITY_SESSION_COOKIE_NAME` / `SECURITY_SESSION_SECURE_COOKIE` | `8h` / `GITGRADER_SESSION` / `true` / `Lax` | Session behavior. |
| `security.content-security-policy` | — | See packaged YAML | CSP sent with the application shell. Loosen only deliberately; the SPA is served from the same origin as the API. |
| `security.result-content-security-policy` | — | See packaged YAML | Stricter CSP sent with the public result page, which is reachable without signing in. |
| `audit.ip-hash-key` | `AUDIT_IP_HASH_KEY` | empty | HMAC key for client-address hashes in audit records. Leaving it empty generates a fresh key at every startup, which is safe but makes address hashes incomparable across restarts, so an address cannot be followed through one. Set a long random value and treat it as a secret; changing it invalidates correlation with existing records. |
| `audit.retention` | `AUDIT_RETENTION` | `P365D` | How long audit events are retained. |

| Framework YAML path | Environment variable | Default |
| --- | --- | --- |
| `spring.datasource.url/username/password` | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | localhost PostgreSQL / `gitgrader` / `gitgrader` |
| `spring.datasource.hikari.maximum-pool-size` | `DB_POOL_SIZE` | `10` |
| `spring.lifecycle.timeout-per-shutdown-phase` | `SPRING_SHUTDOWN_PHASE_TIMEOUT` | `60s` |
| `server.port` | `SERVER_PORT` | `8080` |
| `server.forward-headers-strategy` | `SERVER_FORWARD_HEADERS_STRATEGY` | `none` |
| `springdoc.swagger-ui.enabled` | `SPRINGDOC_UI_ENABLED` | `true`, production `false` |
| `logging.level.org.gitgrader` | `LOG_LEVEL_GITGRADER` | `INFO` |

The `dev` profile moves data under `./target/data`, enables local accounts,
disables secure cookies, exposes all Actuator endpoints, and uses one grader.
The `production` profile disables local accounts, forces secure cookies, limits
Actuator exposure to health/info/prometheus, and disables Swagger UI by default.
