// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from 'react';
import { useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { BrandMark } from '../components/BrandMark';
import { Box, Typography, Paper, Chip, CircularProgress, LinearProgress, Table, TableBody, TableCell, TableHead, TableRow, Alert } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';

/** Human labels for the outcomes a test result can carry. */
const TEST_OUTCOME_LABELS: Record<string, string> = {
  PASSED: 'Passed',
  FAILED: 'Failed',
  ERRORED: 'Errored',
  SKIPPED: 'Skipped'
};

export function PublicResultPage() {
  const { token } = useParams<{ token: string }>();

  useEffect(() => {
    const robots = document.createElement('meta');
    robots.name = 'robots';
    robots.content = 'noindex, nofollow';
    document.head.appendChild(robots);
    const referrer = document.createElement('meta');
    referrer.name = 'referrer';
    referrer.content = 'no-referrer';
    document.head.appendChild(referrer);
    return () => {
      document.head.removeChild(robots);
      document.head.removeChild(referrer);
    };
  }, []);

  const { data, isLoading, error } = useQuery({
    queryKey: ['result', token],
    queryFn: () => api.getResult(token || ''),
    // A token that does not resolve will not start resolving. Retrying left a student
    // who followed a stale link staring at a loading state for eight seconds before
    // being told the link was invalid.
    retry: false
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <CircularProgress aria-label="Loading result" />
      </Box>
    );
  }
  if (error || !data) return <Box sx={{ p: 4 }}><Alert severity="error">Result not found or invalid token.</Alert></Box>;

  // Defensively strip hidden tests
  const safeTests = data.tests.map(test => {
    if (!test.public) {
      return {
        public: false,
        category: test.category || 'Hidden Test',
        outcome: test.outcome,
        hint: test.hint
      };
    }
    return test;
  });

  return (
    <Box sx={{ p: 4, maxWidth: 'md', mx: 'auto' }}>
      <BrandMark />
      <Typography variant="h4" component="h1" gutterBottom>{data.assignmentTitle}</Typography>
      <Typography variant="subtitle1" component="h2" gutterBottom>{data.courseName}</Typography>
      <Paper sx={{ p: 4, mb: 4 }}>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2, flexWrap: 'wrap' }}>
          <Chip label={`Commit: ${data.commitSha.substring(0, 8)}`} sx={{ fontFamily: '"JetBrains Mono", monospace' }} />
          <Chip label={`Received: ${new Date(data.receivedAt).toLocaleString()}`} />
          {data.verified ? (
            <Chip 
              icon={<VerifiedUserIcon />} 
              label="Verified" 
              color="success" 
              aria-label="Commit is verified" 
            />
          ) : (
            <Chip 
              icon={<WarningAmberIcon />} 
              label="Unverified" 
              color="default" 
              aria-label="Commit is unverified" 
            />
          )}
        </Box>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Verified means the commit was signed with a key registered to this student. It does not certify how the work was produced.
        </Typography>

        <Box sx={{ mt: 4, mb: 4 }}>
          <Typography variant="h3" component="h2" gutterBottom>Overall</Typography>
          <Typography variant="body1">{data.passed} of {data.total} tests passed</Typography>
          {typeof data.score === 'number' ? (
            <>
              <Typography variant="body1" sx={{ fontWeight: 'bold' }}>Score: {data.score.toFixed(1)} %</Typography>
              <LinearProgress
                variant="determinate"
                value={data.score}
                sx={{ mt: 1, height: 10, borderRadius: 5 }}
                aria-label="Score progress"
              />
            </>
          ) : (
            // A run that timed out or broke has no score, which is not the same as
            // having scored nothing. Reading the absent value as a number threw here
            // and took the whole page with it.
            <Alert severity="info" sx={{ mt: 1 }}>
              This submission has no score yet. It is either still being graded, or the grading run could not
              finish - your instructor can run it again.
            </Alert>
          )}
        </Box>

        <Typography variant="h3" component="h2" gutterBottom>Test Details</Typography>
        <Table aria-label="Test results table">
          <TableHead>
            <TableRow>
              <TableCell>Test</TableCell>
              <TableCell>Outcome</TableCell>
              <TableCell>Details</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {safeTests.map((test, idx) => (
              <TableRow key={idx}>
                <TableCell>
                  {test.public ? test.name : (test.category || 'Hidden Test')}
                </TableCell>
                <TableCell>
                  {test.outcome === 'PASSED' ? (
                    <Chip size="small" color="success" icon={<CheckCircleIcon />} label="Passed" />
                  ) : (
                    <Chip size="small" color="error" icon={<CancelIcon />} label={TEST_OUTCOME_LABELS[test.outcome] ?? test.outcome} />
                  )}
                </TableCell>
                <TableCell>
                  {test.public ? test.message : test.hint}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
    </Box>
  );
}
