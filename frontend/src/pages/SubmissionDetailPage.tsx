// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useNavigate, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Chip, CircularProgress, Divider, Paper, Typography } from '@mui/material';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { SubmissionStatusChip } from '../components/SubmissionStatusChip';

export function SubmissionDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const query = useQuery({ queryKey: queryKeys.submissions.detail(id), queryFn: () => api.getSubmission(id), enabled: !!id });
  if (query.isLoading) return <CircularProgress aria-label="Loading submission" />;
  if (query.isError || !query.data) return <QueryErrorNotice message="The submission could not be loaded." onRetry={() => void query.refetch()} />;
  const submission = query.data;
  const field = (label: string, value: string) => <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography sx={{ overflowWrap: 'anywhere' }}>{value || '—'}</Typography></Box>;
  return <Box sx={{ maxWidth: 900, display: 'flex', flexDirection: 'column', gap: 2 }}>
    <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}><Typography variant="h4" component="h1">Submission</Typography><Button onClick={() => void navigate('/submissions')}>Back to submissions</Button></Box>
    <Paper variant="outlined" sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}><SubmissionStatusChip status={submission.status} />{submission.late && <Chip size="small" color="warning" label="Late" />}<Chip size="small" variant="outlined" label={submission.signatureStatus} /></Box>
      <Divider />
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
        {field('Commit', submission.commitSha)}{field('Reference', submission.gitRef)}{field('Received', new Date(submission.receivedAt).toLocaleString())}{field('Commit message', submission.commitMessage ?? '')}{field('Signature fingerprint', submission.signatureFingerprint ?? '')}{field('Effective due date', submission.effectiveDueAt ? new Date(submission.effectiveDueAt).toLocaleString() : '')}{field('Rejection reason', submission.rejectionReason ?? '')}{field('Runtime image', submission.runtimeImageDigest ?? '')}
      </Box>
    </Paper>
  </Box>;
}
