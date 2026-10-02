# Instructor workflows

These features use the existing REST API. No server or database changes are required.

- **Saved table views:** open **Table options** to set density and visible columns;
  use **Save view** to name the current filters, sorting and table settings.
  Restore it with **Saved views**. Options also remove views or reset preferences.
  Preferences are scoped to the instructor and page in this browser. Up to 20 views
  are supported per page. No response rows, SSH keys, credentials or result tokens are
  stored. URL filters can also be bookmarked or shared with another authorized instructor.
- **Publication readiness:** assignment details show a checklist for saved materials,
  enabled runtime, opening/deadline and grading settings. Follow **Review configuration**
  to correct draft settings, then save. The checklist is advisory; the existing
  publication rules and server validation remain authoritative.
- **Duplicate as draft:** assignment details offer a reviewed copy into a selected
  course. It keeps material references, grading settings and sandbox limits, starts
  with empty dates and requires a new key within the original course. **Use original
  dates** explicitly restores the old dates. Copies are always drafts and never publish
  automatically. Existing material references may need review before publication.
- **Bulk student verification:** only self-registered students can be selected.
  **Verify selected** opens a confirmation with names and usernames. Each student’s
  current status is rechecked before verification. The dialog reports verified,
  skipped and failed registrations; **Retry failed only** never resubmits successes.
  Requests are sequential and independent, not an atomic bulk transaction. Filters
  constrain the visible selection; student selections and review data are not persisted.
- **Assignment deadlines:** the navigation includes **Deadlines**. Upcoming published
  deadlines are grouped by course; the all view includes past deadlines and drafts.
  Dates display the assignment timezone, falling back to the course timezone.
  **Download calendar** exports the current selection as an `.ics` file using UTC
  instants, escaped text and UTF-8 line folding from
  [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545).
  Individual student extensions are not included. Import is a snapshot, not a subscription.
- **Instructor follow-up:** the dashboard links to pending registrations across the
  student catalogue and the existing infrastructure-failure submission view. Per-class
  missing-work, latest-failed and infrastructure counts cover active enrollments in
  active courses. Counts represent students needing follow-up, not grading-run totals;
  a student may appear in several classes. Links apply the corresponding roster filters.

Browser regression checks use synthetic responses and requests, including verification
and draft creation; they never modify production records. See the README for commands.
