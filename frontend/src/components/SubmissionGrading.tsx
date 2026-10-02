// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Link as RouterLink } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material';
import { api } from '../api';
import type { Submission } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from './QueryErrorNotice';

export function SubmissionGrading({ submission, classId }: { submission: Submission; classId: string }) {
  const query = useQuery({
    queryKey: queryKeys.classStudentReport(submission.courseId, classId, submission.studentId),
    queryFn: () => api.getClassStudentReport(submission.courseId, classId, submission.studentId),
    enabled: !!classId,
    refetchInterval: ['RECEIVED', 'QUEUED', 'RUNNING'].includes(submission.status) ? 5000 : false
  });
  if (!classId) return null;
  const assignment = query.data?.assignments.find((item) => item.assignmentId === submission.assignmentId);
  const matches = assignment?.latestSubmission?.id === submission.id;
  const grading = matches ? assignment.latestGrading : null;

  return <Paper component="section" aria-labelledby="submission-grading-heading" variant="outlined" sx={{ p: { xs: 2, sm: 3 }, overflowWrap: 'anywhere' }}>
    <Typography id="submission-grading-heading" variant="h6" component="h2" gutterBottom>Grading results</Typography>
    {query.isLoading ? <CircularProgress aria-label="Loading grading results" /> : query.isError ? (
      <QueryErrorNotice message="Grading results could not be loaded." onRetry={() => void query.refetch()} />
    ) : !matches ? (
      <Alert severity="info">This submission is not the latest attempt in the class report. Results for older attempts are not available here.</Alert>
    ) : !grading ? (
      <Alert severity={submission.status === 'INFRASTRUCTURE_ERROR' ? 'warning' : 'info'}>
        {submission.status === 'INFRASTRUCTURE_ERROR' ? 'Grading could not finish because of an infrastructure error. No test results are available.' : 'No grading results are available yet.'}
      </Alert>
    ) : (
      <Stack spacing={2}>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <Typography>Attempt {grading.attempt} · {grading.status}</Typography>
          <Typography>{grading.scorePercent == null ? 'Not scored' : `Score: ${grading.scorePercent}%`}</Typography>
          <Typography>{grading.pointsAwarded == null ? 'No points recorded' : `${grading.pointsAwarded} points`}</Typography>
          <Typography>{grading.testsPassed} of {grading.testsTotal} tests passed</Typography>
        </Box>
        {grading.finishedAt && <Typography variant="body2" color="text.secondary">Finished {new Date(grading.finishedAt).toLocaleString()}</Typography>}
        {grading.tests.length === 0 ? <Typography color="text.secondary">No individual test outcomes were recorded.</Typography> : (
          <Box component="ul" sx={{ m: 0, pl: 3 }}>
            {grading.tests.map((test, index) => <Box component="li" key={index} sx={{ mb: 1 }}>
              <Typography variant="body2">
                {test.visibility === 'PUBLIC' ? test.publicName ?? 'Public test' : 'Hidden test'}
                {test.category ? ` · ${test.category}` : ''} · {test.outcome}
                {test.durationMs == null ? '' : ` · ${test.durationMs} ms`}
              </Typography>
              {test.visibility === 'PUBLIC' && test.studentMessage && <Typography variant="body2" color="text.secondary">{test.studentMessage}</Typography>}
            </Box>)}
          </Box>
        )}
      </Stack>
    )}
    <Button component={RouterLink} to={`/students/${encodeURIComponent(submission.studentId)}?courseId=${encodeURIComponent(submission.courseId)}&classId=${encodeURIComponent(classId)}`} sx={{ mt: 2 }}>View student coursework</Button>
  </Paper>;
}
