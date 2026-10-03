// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from 'react';
import { useParams, Link as RouterLink } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import type { PublicResultsOverview } from '../api';
import { queryKeys } from '../api/queryKeys';
import { ApiProblem } from '../api/client';
import { BrandMark } from '../components/BrandMark';
import { Box, Typography, Paper, Chip, CircularProgress, Alert, Stack, Button } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';

type Assignment = PublicResultsOverview['courses'][number]['classes'][number]['assignments'][number];
type Latest = NonNullable<Assignment['latest']>;

/** The last attempt at one assignment, summarised, with a link to its full report. */
function AttemptRow({ token, assignment }: { token: string; assignment: Assignment }) {
  const latest: Latest | null | undefined = assignment.latest;
  return (
    <Box
      component="li"
      sx={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 1,
        alignItems: 'center',
        justifyContent: 'space-between',
        py: 1.5,
        borderTop: '1px solid',
        borderColor: 'divider'
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography component="p" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
          {assignment.title}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {assignment.assignmentKey}
        </Typography>
        {latest ? (
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1, mt: 0.5 }}>
            <Chip size="small" label={latest.shortCommitSha} sx={{ fontFamily: '"JetBrains Mono", monospace' }} />
            <Chip size="small" label={`Received: ${new Date(latest.receivedAt).toLocaleString()}`} />
            {typeof latest.scorePercent === 'number' ? (
              <Chip size="small" color="primary" label={`${latest.scorePercent.toFixed(1)} %`} />
            ) : (
              <Chip size="small" label={latest.gradingStatus ?? 'Not graded'} />
            )}
            {typeof latest.passed === 'boolean' ? (
              latest.passed ? (
                <Chip size="small" color="success" icon={<CheckCircleIcon />} label="Passed" />
              ) : (
                <Chip size="small" color="error" icon={<CancelIcon />} label="Failed" />
              )
            ) : null}
            {latest.late ? <Chip size="small" color="warning" label="Late" /> : null}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            No attempt yet.
          </Typography>
        )}
      </Box>
      {latest ? (
        <Button
          component={RouterLink}
          to={`/results/overview/${encodeURIComponent(token)}/submissions/${encodeURIComponent(latest.submissionId)}`}
          variant="outlined"
          size="small"
        >
          View report
        </Button>
      ) : null}
    </Box>
  );
}

export function PublicResultsOverviewPage() {
  const { token } = useParams<{ token: string }>();

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
    queryKey: queryKeys.resultsOverview(token ?? ''),
    queryFn: () => api.getResultsOverview(token ?? ''),
    enabled: !!token,
    retry: false
  });

  if (!token) {
    return <Box component="main" sx={{ minHeight: '100vh', p: { xs: 2, sm: 4 }, maxWidth: 1200, mx: 'auto', bgcolor: 'background.default' }}><BrandMark /><Alert severity="error">This results link is incomplete.</Alert></Box>;
  }

  if (isLoading) {
    return (
      <Box component="main" sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', p: 3, bgcolor: 'background.default' }}>
        <Box role="status" sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          <BrandMark />
          <CircularProgress aria-label="Loading results overview" />
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
            ? 'Results not found. This link may have been revoked or may never have been valid - ask your instructor for a new one.'
            : 'The results could not be loaded right now. Reload the page in a moment; the link itself is probably fine.'}
        </Alert>
      </Box>
    );
  }
  if (!data) {
    return <Box component="main" sx={{ minHeight: '100vh', p: { xs: 2, sm: 4 }, maxWidth: 1200, mx: 'auto', bgcolor: 'background.default' }}><BrandMark /><Alert severity="error">Results not found or invalid token.</Alert></Box>;
  }

  return (
    <Box component="main" sx={{ minHeight: '100vh', p: { xs: 2, sm: 3, md: 4 }, maxWidth: 1200, width: '100%', mx: 'auto', minWidth: 0, bgcolor: 'background.default' }}>
      <BrandMark />
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3, md: 4 }, minWidth: 0 }}>
        <Typography variant="h4" component="h1" sx={{ overflowWrap: 'anywhere' }}>Your results</Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 0.5 }}>
          {data.studentDisplayName} · updated {new Date(data.generatedAt).toLocaleString()}
        </Typography>

        {data.courses.length === 0 ? (
          <Alert severity="info" sx={{ mt: 3 }}>You are not enrolled in any course with results yet.</Alert>
        ) : null}

        {data.courses.map(course => (
          <Box component="section" key={course.courseId} aria-label={`Results for ${course.courseName}`} sx={{ mt: 3 }}>
            <Typography variant="h5" component="h2" sx={{ overflowWrap: 'anywhere' }}>{course.courseName}</Typography>
            {course.classes.map((classGroup, classIndex) => (
              <Box key={classGroup.classId ?? `${course.courseId}-class-${String(classIndex)}`} sx={{ mt: 1.5 }}>
                {classGroup.className ? (
                  <Typography variant="subtitle1" component="h3" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                    {classGroup.className}
                  </Typography>
                ) : null}
                {classGroup.assignments.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">No assignments in this course yet.</Typography>
                ) : (
                  <Box component="ul" sx={{ listStyle: 'none', p: 0, m: 0 }}>
                    {classGroup.assignments.map(assignment => (
                      <AttemptRow key={assignment.assignmentId} token={token} assignment={assignment} />
                    ))}
                  </Box>
                )}
              </Box>
            ))}
          </Box>
        ))}
      </Paper>
    </Box>
  );
}
