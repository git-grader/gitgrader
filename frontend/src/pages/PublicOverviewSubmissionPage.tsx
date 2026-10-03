// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from 'react';
import { useParams, Link as RouterLink } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { ApiProblem } from '../api/client';
import { BrandMark } from '../components/BrandMark';
import { PublicResultPanel } from '../components/PublicResultPanel';
import { Box, CircularProgress, Alert, Button } from '@mui/material';

/**
 * One submission's detailed report, reached from the public results overview.
 *
 * The overview token stays in the address, so the scoped detail endpoint can verify that
 * the submission belongs to the same student before returning the redacted report.
 */
export function PublicOverviewSubmissionPage() {
  const { token, submissionId } = useParams<{ token: string; submissionId: string }>();

  useEffect(() => {
    const robots = document.createElement('meta');
    robots.name = 'robots';
    robots.content = 'noindex, nofollow';
    document.head.appendChild(robots);
    return () => {
      robots.remove();
    };
  }, []);

  const { data, isLoading, error } = useQuery({
    queryKey: queryKeys.overviewSubmission(token ?? '', submissionId ?? ''),
    queryFn: () => api.getOverviewSubmission(token ?? '', submissionId ?? ''),
    enabled: !!token && !!submissionId,
    retry: false
  });

  if (!token || !submissionId) {
    return <Box component="main" sx={{ minHeight: '100vh', p: { xs: 2, sm: 4 }, maxWidth: 1200, mx: 'auto', bgcolor: 'background.default' }}><BrandMark /><Alert severity="error">This result link is incomplete.</Alert></Box>;
  }

  if (isLoading) {
    return (
      <Box component="main" sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', p: 3, bgcolor: 'background.default' }}>
        <Box role="status" sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          <BrandMark />
          <CircularProgress aria-label="Loading result" />
        </Box>
      </Box>
    );
  }

  if (error) {
    const missing = error instanceof ApiProblem && error.status === 404;
    return (
      <Box component="main" sx={{ minHeight: '100vh', p: { xs: 2, sm: 4 }, maxWidth: 1200, mx: 'auto', bgcolor: 'background.default' }}>
        <BrandMark />
        <Alert severity="error">
          {missing
            ? 'Result not found. This report may have been removed, or it does not belong to this results link.'
            : 'The result could not be loaded right now. Reload the page in a moment; the link itself is probably fine.'}
        </Alert>
        <Button component={RouterLink} to={`/results/overview/${encodeURIComponent(token)}`} sx={{ mt: 2 }}>
          Back to all results
        </Button>
      </Box>
    );
  }
  if (!data) {
    return <Box component="main" sx={{ minHeight: '100vh', p: { xs: 2, sm: 4 }, maxWidth: 1200, mx: 'auto', bgcolor: 'background.default' }}><BrandMark /><Alert severity="error">Result not found or invalid token.</Alert></Box>;
  }

  return (
    <>
      <PublicResultPanel result={data} />
      <Box sx={{ maxWidth: 1200, mx: 'auto', px: { xs: 2, sm: 3, md: 4 }, pb: 4 }}>
        <Button component={RouterLink} to={`/results/overview/${encodeURIComponent(token)}`}>
          Back to all results
        </Button>
      </Box>
    </>
  );
}
