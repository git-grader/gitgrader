// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Chip, CircularProgress, Divider, Paper, Stack, TextField, Typography } from '@mui/material';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import { PageHeader } from '../components/PageHeader';
import type { ClassStudentReport, StudentUpdate } from '../api';

function Coursework({ report, courseId, classId }: {
  report: ClassStudentReport;
  courseId: string;
  classId: string;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
        <Typography variant="h6" component="h2">Coursework</Typography>
        <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}/classes/${encodeURIComponent(classId)}`}>
          Back to class
        </Button>
      </Box>
      {report.assignments.length === 0 ? (
        <Typography color="text.secondary">No coursework is available in this class.</Typography>
      ) : report.assignments.map((assignment) => {
        const submission = assignment.latestSubmission;
        const grading = assignment.latestGrading;
        return (
          <Box key={assignment.assignmentId} component="section" aria-labelledby={`coursework-${assignment.assignmentId}`}>
            <Typography id={`coursework-${assignment.assignmentId}`} variant="subtitle1">{assignment.title}</Typography>
            <Typography variant="body2" color="text.secondary">
              Best result: {assignment.bestPercent}% ({assignment.bestPoints} points)
            </Typography>
            {!submission ? (
              <Typography variant="body2" sx={{ mt: 1 }}>No submission yet.</Typography>
            ) : (
              <Stack spacing={1} sx={{ mt: 1 }}>
                <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
                  <Chip size="small" variant="outlined" label={submission.status} />
                  {submission.late && <Chip size="small" color="warning" label="Late" />}
                  <Typography variant="body2">Submitted {new Date(submission.receivedAt).toLocaleString()}</Typography>
                </Stack>
                <Typography variant="body2">Commit {submission.commitSha.slice(0, 12)} · {submission.gitRef}</Typography>
                {submission.commitMessage && <Typography variant="body2">{submission.commitMessage}</Typography>}
                {!grading && submission.status === 'INFRASTRUCTURE_ERROR' && (
                  <Alert severity="warning">Infrastructure error; no student test results are available.</Alert>
                )}
                {!grading && submission.status !== 'INFRASTRUCTURE_ERROR' && (
                  <Typography variant="body2">Not graded yet</Typography>
                )}
                {grading && (
                  <>
                    <Typography variant="body2">
                      Latest attempt: {grading.scorePercent == null ? 'Not graded' : `${grading.scorePercent}%`}
                      {grading.pointsAwarded == null ? '' : ` · ${grading.pointsAwarded} points`}
                      {' · '}{grading.attempt === 1 ? 'Attempt 1' : `Attempt ${grading.attempt}`}
                    </Typography>
                    <Typography variant="body2">
                      {grading.testsPassed} of {grading.testsTotal} tests passed
                      {grading.passed === null || grading.passed === undefined ? '' : grading.passed ? ' · Passed' : ' · Not passed'}
                    </Typography>
                    {grading.finishedAt && <Typography variant="body2">Graded {new Date(grading.finishedAt).toLocaleString()}</Typography>}
                    {grading.tests.length > 0 && (
                      <Stack spacing={0.5} component="ul" sx={{ my: 0, pl: 3 }}>
                        {grading.tests.map((test, index) => (
                          <Box component="li" key={`${assignment.assignmentId}-${index}`}>
                            <Typography variant="body2">
                              {test.visibility === 'HIDDEN' ? 'Hidden test' : test.publicName ?? 'Public test'}
                              {test.category ? ` · ${test.category}` : ''}
                              {` · ${test.outcome}`}
                              {test.durationMs == null ? '' : ` · ${test.durationMs} ms`}
                            </Typography>
                            {test.visibility === 'PUBLIC' && test.studentMessage && (
                              <Typography variant="caption" color="text.secondary">{test.studentMessage}</Typography>
                            )}
                          </Box>
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Stack>
            )}
            <Divider sx={{ mt: 2 }} />
          </Box>
        );
      })}
    </Paper>
  );
}

export function StudentDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const location = useLocation();
  const search = new URLSearchParams(location.search);
  const courseId = search.get('courseId') ?? '';
  const classId = search.get('classId') ?? '';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.students.detail(id), queryFn: () => api.getStudent(id), enabled: !!id });
  const courseworkQuery = useQuery({
    queryKey: queryKeys.classStudentReport(courseId, classId, id),
    queryFn: () => api.getClassStudentReport(courseId, classId, id),
    enabled: !!courseId && !!classId && !!id
  });
  const student = query.data?.student;
  const sshKeys = query.data?.sshKeys ?? [];
  const [form, setForm] = useState<StudentUpdate | null>(null);
  const [keyForm, setKeyForm] = useState({ label: '', publicKey: '', reason: '' });
  const mutation = useMutation({
    mutationFn: (update: StudentUpdate) => api.updateStudent(id, update),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.students.detail(id), { student: updated, sshKeys: query.data?.sshKeys ?? [] });
      void queryClient.invalidateQueries({ queryKey: ['students', 'list'] });
      void navigate('/students');
    }
  });
  const statusMutation = useMutation({
    mutationFn: ({ status, reason }: { status: string; reason: string }) =>
      api.changeStudentStatus(id, status, reason),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.students.detail(id), {
        student: { ...student, status: updated.status },
        sshKeys: query.data?.sshKeys ?? []
      });
      void queryClient.invalidateQueries({ queryKey: ['students', 'list'] });
    }
  });
  const registerKeyMutation = useMutation({
    mutationFn: () => api.registerStudentKey(id, keyForm),
    onSuccess: () => {
      setKeyForm({ label: '', publicKey: '', reason: '' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.students.detail(id) });
    }
  });
  const revokeKeyMutation = useMutation({
    mutationFn: ({ keyId, reason }: { keyId: string; reason: string }) =>
      api.revokeStudentKey(id, keyId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.students.detail(id) });
    }
  });

  if (query.isLoading || !student && !query.isError) return <CircularProgress aria-label="Loading student" />;
  if (query.isError || !student) return <QueryErrorNotice message="The student could not be loaded." onRetry={() => void query.refetch()} />;

  const values = form ?? {
    studentUsername: student.studentUsername,
    firstName: student.firstName,
    lastName: student.lastName,
    email: student.email
  };
  const errors = problemFieldErrors(mutation.error);

  return (
    <Box sx={{ maxWidth: 720, width: '100%', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 3 }}>
      <PageHeader title="Edit Student" description={`${student.firstName} ${student.lastName} · ${student.studentUsername}`} />
      <MutationErrorAlert error={statusMutation.error ?? revokeKeyMutation.error} />
      <Paper variant="outlined" sx={{ p: 2, display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography variant="body2" color="text.secondary">Status: {student.status}</Typography>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button size="small" variant="outlined" disabled={statusMutation.isPending} onClick={() => statusMutation.mutate({ status: 'VERIFIED_BY_INSTRUCTOR', reason: 'Verified by instructor' })}>
              Verify
            </Button>
            <Button size="small" variant="outlined" color="warning" disabled={statusMutation.isPending} onClick={() => { const reason = window.prompt('Suspension reason:', 'Suspended by instructor'); if (reason) statusMutation.mutate({ status: 'SUSPENDED', reason }); }}>
              Suspend
            </Button>
            <Button size="small" variant="outlined" color="error" disabled={statusMutation.isPending} onClick={() => { if (window.confirm('Archive this student?')) statusMutation.mutate({ status: 'ARCHIVED', reason: 'Archived by instructor' }); }}>
              Archive
            </Button>
            <Button size="small" variant="outlined" disabled={statusMutation.isPending} onClick={() => statusMutation.mutate({ status: 'RESTORE', reason: 'Restored by instructor' })}>
              Restore
            </Button>
          </Box>
      </Paper>
      {courseId && classId && (
        courseworkQuery.isLoading ? (
          <Box sx={{ p: 2 }}><CircularProgress aria-label="Loading coursework" /></Box>
        ) : courseworkQuery.isError || !courseworkQuery.data ? (
          <QueryErrorNotice message="Coursework could not be loaded." onRetry={() => void courseworkQuery.refetch()} />
        ) : (
          <Coursework report={courseworkQuery.data} courseId={courseId} classId={classId} />
        )
      )}
      <Paper variant="outlined" component="form" sx={{ p: { xs: 2, sm: 3 }, display: 'flex', flexDirection: 'column', gap: 2 }} onSubmit={(event) => {
        event.preventDefault();
        mutation.mutate(values);
      }}>
        <MutationErrorAlert error={mutation.error} />
        <TextField label="Student ID / Username" required value={values.studentUsername} onChange={(e) => setForm({ ...values, studentUsername: e.target.value })} error={!!errors.studentUsername} helperText={errors.studentUsername} disabled={mutation.isPending} />
        <TextField label="First Name" required value={values.firstName} onChange={(e) => setForm({ ...values, firstName: e.target.value })} error={!!errors.firstName} helperText={errors.firstName} disabled={mutation.isPending} />
        <TextField label="Last Name" required value={values.lastName} onChange={(e) => setForm({ ...values, lastName: e.target.value })} error={!!errors.lastName} helperText={errors.lastName} disabled={mutation.isPending} />
        <TextField label="Email" type="email" required value={values.email} onChange={(e) => setForm({ ...values, email: e.target.value })} error={!!errors.email} helperText={errors.email} disabled={mutation.isPending} />
        <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
          <Button onClick={() => { void navigate('/students'); }} disabled={mutation.isPending}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>{mutation.isPending ? 'Saving...' : 'Save'}</Button>
        </Box>
      </Paper>

      <Paper variant="outlined" sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Typography variant="h6">SSH Keys</Typography>
        {sshKeys.length === 0 ? (
          <Typography variant="body2" color="text.secondary">No keys registered.</Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {sshKeys.map((key) => (
              <Paper key={key.id} variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="subtitle2">{key.label}</Typography>
                  <Chip size="small" label={key.status} color={key.status === 'ACTIVE' ? 'success' : 'default'} />
                  <Chip size="small" variant="outlined" label={key.keyType} />
                  <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{key.fingerprint}</Typography>
                </Box>
                <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{key.publicKey}</Typography>
                {key.status === 'ACTIVE' && (
                  <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button
                      size="small"
                      color="warning"
                      disabled={revokeKeyMutation.isPending}
                      onClick={() => { const reason = window.prompt('Revocation reason:', 'Revoked by instructor'); if (reason) revokeKeyMutation.mutate({ keyId: key.id, reason }); }}
                    >
                      Revoke
                    </Button>
                  </Box>
                )}
              </Paper>
            ))}
          </Box>
        )}
        <MutationErrorAlert error={registerKeyMutation.error} />
        <Box component="form" sx={{ display: 'flex', flexDirection: 'column', gap: 2 }} onSubmit={(event) => { event.preventDefault(); registerKeyMutation.mutate(); }}>
          <TextField label="Label" required value={keyForm.label} onChange={(e) => setKeyForm({ ...keyForm, label: e.target.value })} disabled={registerKeyMutation.isPending} />
          <TextField label="Public Key" required multiline minRows={2} value={keyForm.publicKey} onChange={(e) => setKeyForm({ ...keyForm, publicKey: e.target.value })} disabled={registerKeyMutation.isPending} />
          <TextField label="Reason" required value={keyForm.reason} onChange={(e) => setKeyForm({ ...keyForm, reason: e.target.value })} disabled={registerKeyMutation.isPending} />
          <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="submit" variant="outlined" disabled={registerKeyMutation.isPending}>
              {registerKeyMutation.isPending ? 'Registering…' : 'Register key'}
            </Button>
          </Box>
        </Box>
      </Paper>
    </Box>
  );
}
