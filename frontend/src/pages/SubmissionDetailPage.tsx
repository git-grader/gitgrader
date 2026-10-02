// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Link as RouterLink, useNavigate, useParams, useSearchParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, Chip, CircularProgress, Divider, Paper, Typography } from '@mui/material';
import { SubmissionGrading } from '../components/SubmissionGrading';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { PageHeader } from '../components/PageHeader';
import { SubmissionStatusChip } from '../components/SubmissionStatusChip';

export function SubmissionDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const classId = searchParams.get('classId') ?? '';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const regrade = useMutation({
    mutationFn: () => api.regradeSubmission(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.submissions.detail(id) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.submissions.all });
      void queryClient.invalidateQueries({ queryKey: ['report'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
    }
  });
  const query = useQuery({ queryKey: queryKeys.submissions.detail(id), queryFn: () => api.getSubmission(id), enabled: !!id, refetchInterval: (current) => ['RECEIVED', 'QUEUED', 'RUNNING'].includes(current.state.data?.status ?? '') ? 5000 : false });
  if (query.isLoading) return <CircularProgress aria-label="Loading submission" />;
  if (query.isError || !query.data) return <QueryErrorNotice message="The submission could not be loaded." onRetry={() => void query.refetch()} />;
  const submission = query.data;
  const field = (label: string, value: string) => <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography sx={{ overflowWrap: 'anywhere' }}>{value || '—'}</Typography></Box>;
  return <Box sx={{ maxWidth: 900, width: '100%', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 3 }}>
    <PageHeader title="Submission" actions={
      <>
        <Button variant="outlined" onClick={() => regrade.mutate()} disabled={regrade.isPending}>{regrade.isPending ? 'Queueing…' : 'Regrade'}</Button>
        <Button component={RouterLink} to={`/submissions?courseId=${encodeURIComponent(submission.courseId)}&studentId=${encodeURIComponent(submission.studentId)}&assignmentId=${encodeURIComponent(submission.assignmentId)}`}>View student attempts</Button>
        <Button onClick={() => void navigate('/submissions')}>Back to submissions</Button>
      </>
    } />
    {regrade.isError && <QueryErrorNotice message="The submission could not be queued for regrading." onRetry={() => regrade.mutate()} />}
    {regrade.isSuccess && <Typography color="success.main">Regrade queued.</Typography>}
    <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 }, display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}><SubmissionStatusChip status={submission.status} />{submission.late && <Chip size="small" color="warning" label="Late" />}<Chip size="small" variant="outlined" label={submission.signatureStatus} /></Box>
      <Divider />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
        {field('Student', submission.studentUsername ?? 'Unknown student')}{field('Commit', submission.commitSha)}{field('Reference', submission.gitRef)}{field('Received', new Date(submission.receivedAt).toLocaleString())}{field('Commit message', submission.commitMessage ?? '')}{field('Signature fingerprint', submission.signatureFingerprint ?? '')}{field('Effective due date', submission.effectiveDueAt ? new Date(submission.effectiveDueAt).toLocaleString() : '')}{field('Rejection reason', submission.rejectionReason ?? '')}{field('Runtime image', submission.runtimeImageDigest ?? '')}
      </Box>
    </Paper>
    <SubmissionGrading key={`${submission.id}:${classId}`} submission={submission} classId={classId} />
  </Box>;
}
