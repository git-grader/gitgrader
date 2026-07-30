# Privacy

GitGrader processes information needed to operate assignments: account and
authentication attributes, registered SSH public keys, course memberships,
assignment and submission metadata, submitted Git content, grading outputs, and
audit records. Instructors are responsible for choosing a lawful basis,
retention period, access policy, and notice appropriate to their deployment.

Self-registration does not verify a person’s identity. An SSH key associates
submissions with the account that registered it; it is not identity proof.

## Retention and access

Keep student submissions, results, audit records, templates, hidden tests, and
runtime definitions only as long as the course and applicable policy require.
Backups retain the same information until they expire and must have access
controls equivalent to production. Archival should preserve the exact template,
hidden tests, runtime definition, commit, and grade needed to explain a result.

Use application administration and the underlying database only through an
authorized process to export a student’s account metadata, registered keys,
course membership, submissions, and results. This repository does not document
a dedicated self-service data-export or deletion REST endpoint because such an
endpoint is not established here; validate your deployed version’s API before
promising one.

Deletion and anonymisation must account for relational records, Git repository
content, grading artifacts, application logs, backups, and legal retention
requirements. Do not erase an audit trail where a documented academic or legal
retention obligation applies; restrict access or anonymise identifiers instead
where appropriate.

## Result links

Result URLs use an opaque token and must contain no student name, email address,
account identifier, course identifier, or other personal identifier. Treat the
token as a bearer secret: avoid putting it in logs, tickets, browser history
exports, or referrers, and revoke it when access should end.
