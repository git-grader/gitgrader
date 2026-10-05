# Security policy

## Supported versions

Security fixes are made on the latest released minor version. No release has been
published yet, so `main` is the supported line and the version table below changes
when the first tag is cut.

| Version | Supported |
| --- | --- |
| `main` (no release yet) | Yes |
| 0.1.x | Once 0.1.0 is tagged |
| Earlier versions | No |

## Reporting a vulnerability

Use [GitHub Security Advisories](https://github.com/git-grader/gitgrader/security/advisories/new)
for private reports. Do not open a public issue, commit a proof of concept, or
include credentials, student work, hidden tests, result tokens, or private keys
in a report.

Include a clear reproduction, affected version or commit, impact assessment,
and any proposed mitigation. Acknowledgement is targeted within 3 business days;
an initial assessment within 7 business days; and status updates at least every
14 days until resolution. These are targets, not a service-level agreement.

## Coordinated disclosure

Please give maintainers reasonable time to investigate and release a fix before
public disclosure. The project will credit reporters who want attribution after
a fix is available, unless doing so would expose them or users to risk.

## Scope

In scope are vulnerabilities in code and official deployment assets that permit
unauthorized access, disclosure or modification of student data, hidden tests,
grading infrastructure, credentials, or the host; authentication and
authorization bypasses; injection; unsafe sandbox escape paths; and secret
exposure.

Out of scope are social engineering, denial of service requiring unrestricted
local or administrative access, findings solely in unsupported dependencies
without a demonstrable impact here, and expected behavior. In particular, a
student being able to see public tests is **not** a vulnerability. A verified
commit signature is also not evidence of unaided authorship.

Deployment choices can create risk outside the application boundary. In
particular, access to the Docker socket is effectively host-root access; see
[`docs/security.md`](docs/security.md) before enabling grading on a shared host.

## Hall of fame

No reports have been credited yet.
