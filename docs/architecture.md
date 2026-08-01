# Architecture

GitGrader is a modular monolith: one deployable Spring Boot application with
explicit, tested module boundaries. This keeps installation, transactional
integrity, and operational ownership simple while preventing the unstructured
coupling typical of a monolith. Spring Modulith verifies the dependencies
declared by each module’s `package-info.java`.

```mermaid
flowchart TB
  student([Student]):::person
  instructor([Instructor]):::person

  subgraph gg["GitGrader (what this project runs)"]
    direction TB
    app["<b>Application</b><br/>Spring Boot, Java 25<br/><i>One process: web interface, REST API,<br/>Git over SSH, and grading on a schedule</i>"]:::container
    db[("<b>PostgreSQL</b><br/><i>Coursework, submissions, grading runs,<br/>audit trail, job queue</i>")]:::store
    vols[("<b>Storage volumes</b><br/><i>Bare repositories, templates,<br/>hidden tests, artifacts</i>")]:::store
  end

  directory["<b>Directory</b><br/><i>LDAP</i>"]:::external
  sandbox["<b>Grading sandbox</b><br/><i>Throwaway container, no network</i>"]:::external

  student -->|"clone, push, read a result<br/>SSH and HTTPS"| app
  instructor -->|"manage coursework<br/>HTTPS"| app
  app -->|"JDBC"| db
  app -->|"file I/O"| vols
  app -->|"checks credentials<br/>LDAP"| directory
  app -->|"starts one per submission<br/>Docker API"| sandbox

  classDef person fill:#0D162C,color:#fff,stroke:#0D162C
  classDef container fill:#2563EB,color:#fff,stroke:#1e4fc4
  classDef store fill:#475569,color:#fff,stroke:#334155
  classDef external fill:#DCE3EA,color:#0D162C,stroke:#94a3b8
  style gg fill:#F5F7FA,stroke:#DCE3EA
```

**Reading it.** A rounded dark box is a person. Inside the shaded boundary is
what this project runs: a plain box is a process, a cylinder is where state
lives. A pale box outside the boundary is something it talks to but does not
own. Each arrow is labelled with what crosses it and over which protocol.

The single application box is the point. HTTP, SSH and grading are one process,
not three services, so a submission is recorded and queued in one transaction
and there is one thing to install and back up. The sandbox is outside the
boundary because it is deliberately not ours: it is created per submission,
given no network, and destroyed.

## Module structure

Fifteen modules, each declaring in its `package-info.java` exactly which others
it may use. Spring Modulith fails the build when code crosses a boundary that
was not declared, so the table below is enforced rather than aspirational.

```mermaid
C4Component
  title Components: the modules, by what they are allowed to depend on
  Container_Boundary(entry, "Entry points") {
    Component(git, "git", "SSH and JGit", "Admits pushes, provisions repositories")
    Component(api, "api", "Spring MVC", "REST API and the SPA shell")
  }
  Container_Boundary(domain, "Domain") {
    Component(registration, "registration", "", "Self-registration and key enrolment")
    Component(grading, "grading", "", "Queue, runner, scoring")
    Component(reports, "reports", "", "Course progress and exports")
    Component(security, "security", "", "Authentication, result tokens, rate limits")
    Component(submissions, "submissions", "", "Immutable record of every push")
    Component(assignments, "assignments", "", "Assignments and deadlines")
  }
  Container_Boundary(foundation, "Foundation: depend on nothing") {
    Component(identity, "identity", "", "Students")
    Component(courses, "courses", "", "Courses and classes")
    Component(templates, "templates", "", "Starter projects, hidden tests")
    Component(runtimes, "runtimes", "", "Pinned grading images")
    Component(sshkeys, "sshkeys", "", "Registered public keys")
    Component(audit, "audit", "", "Append-only trail")
    Component(configuration, "configuration", "", "Typed settings")
  }
  Rel(git, submissions, "Records an accepted push")
  Rel(git, registration, "Reacts to a new student")
  Rel(api, reports, "Reads course progress")
  Rel(api, grading, "Reads a result")
  Rel(grading, submissions, "Grades")
  Rel(submissions, assignments, "Belongs to")
  Rel(assignments, courses, "Belongs to")
  Rel(security, identity, "Resolves who is asking")
  UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

**Reading it.** Boxes are modules, grouped by how far they sit from the outside
world. The three arrows are the direction dependencies are allowed to run:
downward only. Nothing in Foundation may reach up, which is why those seven
modules have no declared dependencies at all.

The diagram shows the shape; this table is the contract, and matches each
`package-info.java` exactly:

| Module | May depend on |
|---|---|
| `audit`, `configuration`, `courses`, `identity`, `runtimes`, `sshkeys`, `templates` | *nothing* |
| `assignments` | courses, templates, runtimes, identity |
| `submissions` | identity, assignments, courses, sshkeys |
| `security` | identity, submissions |
| `grading` | submissions, assignments, runtimes, templates |
| `reports` | identity, courses, assignments, submissions |
| `registration` | identity, sshkeys, courses, security |
| `api` | identity, sshkeys, reports, courses, assignments, submissions, grading, security |
| `git` | identity, sshkeys, courses, assignments, templates, submissions, grading, security, registration |

Two dependencies that would otherwise be cycles are carried by events instead.
`submissions` publishes `SubmissionRecorded` and `grading` consumes it, so
recording a push never waits for a grade. `registration` publishes
`StudentRegistered` and `git` consumes it to create the student's repositories,
so registering never waits for a dozen repositories to appear on disk. Spring
Modulith persists both publications and replays anything outstanding at
restart, so no broker is involved.

## How a submission becomes a result

```mermaid
sequenceDiagram
    autonumber
    actor S as Student
    participant SSH as Git endpoint
    participant DB as PostgreSQL
    participant W as Grading worker
    participant Sandbox as Sandbox container

    S->>SSH: git push (signed commit)
    SSH->>SSH: Key registered? Signature valid? Deadline passed?<br/>Size, commit count, duplicate, allowance?
    alt Anything fails
        SSH-->>S: Rejected, with the reason and how to fix it
    else Accepted
        SSH->>DB: Record submission, supersede any unstarted run, queue a job
        SSH-->>S: Accepted, plus a result link
        W->>DB: Claim the job (one per student, SKIP LOCKED)
        W->>Sandbox: Start it: student's code, hidden tests read-only, no network
        Sandbox-->>W: Test output and exit code
        W->>DB: Store score and per-test outcomes
        S->>DB: Open the result link
        DB-->>S: Score, with hidden checks reduced to a category and a hint
    end
```

**Reading it.** Time runs downward. A solid arrow is a request, a dashed one a
reply. The `alt` block is a branch: either the push is refused or it proceeds.

Steps 2 and 3 are where most of the value sits: a push is refused early, in the
one place the student is already looking, with a message telling them what to
change. Step 6 is why the queue is in PostgreSQL rather than a broker: claiming
a job is a row lock in the same database as the submission, so a worker cannot
lose or duplicate work. Step 11 is the whole of what a student is shown about a
hidden check: a category and a hint, never its name or the assertion it made.

## Queue and runner

Grading work is also represented by `grading_jobs`. PostgreSQL is the queue:
workers claim pending rows using `SELECT … FOR UPDATE SKIP LOCKED`, with
priority, availability, claim expiry, attempt count, and a partial runnable-job
index. This provides concurrent-worker semantics without operating RabbitMQ or
another broker; a narrow runner interface leaves room for a future broker.

The claim is also where fairness lives. A plain FIFO let one student own the
queue, because a loop of pushes produced a job per push and every one of them
sat in front of the rest of the course. The claim now reduces the candidates to
each student's oldest job and skips students who already occupy a worker, so a
student holds at most one worker however many assignments they have queued and a
backlog drains in round-robin order rather than in submission order.

Coalescing keeps the queue short in the first place. A partial unique index
allows one `PENDING` job per student and assignment, so queueing a newer
submission withdraws the older one as `CANCELLED` rather than adding to it. Work
a worker already claimed is never withdrawn: cancelling a running sandbox would
abandon its container and workspace. Every push is still recorded, so the
attempt history the product promises is unchanged — a superseded submission is
one that exists and was not graded, not one that was lost.

`GradingRunner` is the only intended path to execute untrusted code. The Docker
implementation runs a short-lived non-root container with network disabled by
capabilities, and no-new-privileges. To add a runner, implement the runner
contract, preserve the run’s timeout/resources/artifact/report semantics, select
it through `grading.runner`, and add integration tests proving hidden tests and
student work mounts retain their access controls. Do not use process spawning in

## Data model and history

UUIDs avoid exposing sequential enrolment or submission counts. All meaningful
timestamps are `TIMESTAMPTZ`. Core relationships are students/instructors,
keys, courses/enrolments, runtimes, templates/test suites, assignments,
repositories, submissions, grading runs/jobs/results/logs, result tokens, and
audit events.

`ssh_keys`, `submissions`, `grading_runs`, `template_versions`, and
`test_suite_versions` preserve history rather than destructively rewriting it.
Keys are revoked/replaced; submissions have no `updated_at`; regrades append an
attempt; published templates and test suites retain their content-addressed
version; runtimes retain the immutable image digest. This makes a historical
grade explainable from the accepted commit, versions, runner image, and run.

Score percentage is `tests_passed / tests_total × 100`. For example, 7 passing
tests out of 10 is `7 / 10 × 100 = 70.0 %`.
