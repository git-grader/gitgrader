# Instructor and administrator workspace

GitGrader has one web workspace for instructors and administrators. Students do
not sign in: they register an SSH public key, push signed commits over SSH, and
use the result link returned after a submission to read feedback.

## Roles and navigation

Instructors can use Dashboard, Students, Courses, Assignments, Materials, and
Submissions. Administrators receive the same workspace plus an **Administration**
navigation group for Audit log, Settings, and Runtimes. The server remains the
authority for every route; hiding an administrator item does not grant access.

## Instructor workflow

1. Create a course, set its timezone and registration window, then add classes.
   Registration must be enabled and within its window before students can register.
2. Review and manage students from the course context. Verification, suspension,
   restoration, and archiving have different effects; confirm destructive changes
   before continuing. SSH keys identify students for signed Git pushes.
3. Create public starter templates and confidential test suites in Materials.
   Upload versions, then publish the version intended for use. Do not expose test
   suite contents outside this workspace.
4. Create an assignment and select its course, template version, test-suite version,
   runtime, schedule, and grading limits. A draft cannot be published until those
   prerequisites are present and the opening/deadline order is valid. Published
   assignments are immutable.
5. Use submissions, course reports, and class progress to investigate work. Result
   links are student-facing and need no web login. Export reports when a snapshot is
   needed outside GitGrader.

## Data loading

Courses, assignments, course choices, templates, and test suites are bounded
management collections. The UI loads every API page before it filters or sorts
them, so a valid record is never hidden by a stale server-page index. Students
follow the same policy. Submissions and audit history remain server-paginated
because they grow without a practical browser-side bound.

## Browser verification

At desktop and mobile widths, check that the sign-in page states that it is for
instructors and administrators, while students are directed to registration and
result links. As an instructor, verify the main task navigation, responsive lists,
and create/edit flows. As an administrator, also verify the separate Administration
group and its audit, runtime, and settings routes. Confirm that empty and failed
views provide distinct guidance and that destructive actions keep their confirmations.

For local credentials and the complete end-to-end walkthrough, use the
[manual testing guide](manual-testing.md).

### Visual review checklist

Review the workspace at 1440px and 390px widths before accepting a UI change:

- Page-header actions remain visible, reachable, and wrap without overlap.
- Navigation remains reachable on mobile, and administrator routes stay within the
   labelled Administration group.
- Results tables remain contained by their section; horizontal scrolling belongs to
   the table, never the page.
- Success, attention, and error states remain distinguishable with readable labels.
- Public registration and result pages show one focused task and never show
   authenticated instructor navigation.