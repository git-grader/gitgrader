// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import type { PublicResult } from '../api';
import { BrandMark } from './BrandMark';
import { Box, Typography, Paper, Chip, LinearProgress, Table, TableBody, TableCell, TableHead, TableRow, TableContainer, Alert } from '@mui/material';
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

/**
 * The detailed, already-redacted result for a single submission.
 *
 * Extracted from the public result page so the overview page can show the same report
 * behind its scoped links without reimplementing the redaction.
 */
export function PublicResultPanel({ result }: { result: PublicResult }) {
  // Defensively strip hidden tests
  const safeTests = result.tests.map(test => {
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
    <Box component="main" sx={{ minHeight: '100vh', p: { xs: 2, sm: 3, md: 4 }, maxWidth: 1200, width: '100%', mx: 'auto', minWidth: 0, bgcolor: 'background.default' }}>
      <BrandMark />
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3, md: 4 }, minWidth: 0 }}>
        <Typography variant="h4" component="h1" sx={{ overflowWrap: 'anywhere' }}>{result.assignmentTitle}</Typography>
        <Typography variant="h6" component="p" color="text.secondary" sx={{ mt: 0.5, overflowWrap: 'anywhere' }}>{result.courseName}</Typography>

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(0, 1.6fr) minmax(280px, 0.85fr)' }, gap: { xs: 3, md: 4 }, alignItems: 'start', mt: 3 }}>
          <Box component="section" aria-labelledby="test-details-heading" sx={{ minWidth: 0 }}>
            <Typography id="test-details-heading" variant="h5" component="h2" sx={{ mb: 1.5 }}>Test Details</Typography>
            <TableContainer tabIndex={0} role="region" aria-label="Test results" sx={{ '&:focus-visible': { outline: '2px solid', outlineColor: 'primary.main', outlineOffset: -2 }, width: '100%', overflowX: 'auto' }}>
              <Table aria-label="Test results table" sx={{ minWidth: 520 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Test</TableCell>
                    <TableCell>Outcome</TableCell>
                    <TableCell>Details</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {safeTests.map((test, idx) => (
                    <TableRow key={`${test.outcome}-${String(idx)}`}>
                      <TableCell sx={{ overflowWrap: 'anywhere' }}>
                        {test.public ? test.name : (test.category || 'Hidden Test')}
                      </TableCell>
                      <TableCell>
                        {test.outcome === 'PASSED' ? (
                          <Chip size="small" color="success" icon={<CheckCircleIcon />} label="Passed" />
                        ) : (
                          <Chip size="small" color="error" icon={<CancelIcon />} label={TEST_OUTCOME_LABELS[test.outcome] ?? test.outcome} />
                        )}
                      </TableCell>
                      <TableCell sx={{ overflowWrap: 'anywhere' }}>
                        {test.public ? test.message : test.hint}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>

          <Box
            component="section"
            aria-labelledby="overall-heading"
            sx={{ minWidth: 0, borderColor: 'divider', borderTop: { xs: 1, md: 0 }, borderLeft: { xs: 0, md: 1 }, pt: { xs: 3, md: 0 }, pl: { xs: 0, md: 3 } }}
          >
            <Typography id="overall-heading" variant="h5" component="h2" sx={{ mb: 1.5 }}>Overall</Typography>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', flexWrap: 'wrap', mb: 1.5 }}>
              <Chip label={`Commit: ${result.commitSha.substring(0, 8)}`} sx={{ maxWidth: '100%', fontFamily: '"JetBrains Mono", monospace', '& .MuiChip-label': { overflowWrap: 'anywhere' } }} />
              <Chip label={`Received: ${new Date(result.receivedAt).toLocaleString()}`} sx={{ maxWidth: '100%', '& .MuiChip-label': { whiteSpace: 'normal', overflowWrap: 'anywhere' } }} />
              {result.verified ? (
                <Chip icon={<VerifiedUserIcon />} label="Verified" color="success" aria-label="Commit is verified" />
              ) : (
                <Chip icon={<WarningAmberIcon />} label="Unverified" color="default" aria-label="Commit is unverified" />
              )}
            </Box>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Verified means the commit was signed with a key registered to this student. It does not certify how the work was produced.
            </Typography>
            {typeof result.passed === 'number' && typeof result.total === 'number' ? (
              <Typography variant="body2">{result.passed} of {result.total} tests passed</Typography>
            ) : (
              <Typography variant="body2">No checks have been recorded for this submission yet.</Typography>
            )}
            {typeof result.score === 'number' ? (
              <>
                <Typography variant="h4" component="p" sx={{ fontWeight: 700, mt: 1 }}>Score: {result.score.toFixed(1)} %</Typography>
                <LinearProgress
                  variant="determinate"
                  value={result.score}
                  sx={{ mt: 1, height: 8, borderRadius: 1 }}
                  aria-label="Score progress"
                />
              </>
            ) : (
              <Alert severity="info" sx={{ mt: 1 }}>
                This submission has no score yet. It is either still being graded, or the grading run could not
                finish - your instructor can run it again.
              </Alert>
            )}
          </Box>
        </Box>
      </Paper>
    </Box>
  );
}
