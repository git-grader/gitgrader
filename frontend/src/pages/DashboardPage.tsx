// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { Alert, Box, Typography, Grid, Paper, CircularProgress } from '@mui/material';

export function DashboardPage() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: api.getDashboard
  });

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress aria-label="Loading dashboard" /></Box>;
  if (isError || !data) {
    return <QueryErrorNotice message="The dashboard could not be loaded." onRetry={() => void refetch()} />;
  }

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Dashboard</Typography>

      {/* The count was fetched and then dropped, so the one number that says grading
          itself is broken - as opposed to students failing - was never shown anywhere. */}
      {data.failedInfrastructureCount > 0 && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          {data.failedInfrastructureCount} grading {data.failedInfrastructureCount === 1 ? 'run' : 'runs'} could not be
          carried out. This is a platform fault rather than a student one, and the affected submissions can be graded
          again.
        </Alert>
      )}

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <Typography variant="h3">{data.courseCount}</Typography>
            <Typography variant="subtitle1">Courses</Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <Typography variant="h3">{data.studentCount}</Typography>
            <Typography variant="subtitle1">Students</Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <Typography variant="h3">{data.openAssignmentCount}</Typography>
            <Typography variant="subtitle1">Open Assignments</Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper sx={{ p: 3, textAlign: 'center' }}>
            <Typography variant="h3">{data.runningGradingCount}</Typography>
            <Typography variant="subtitle1">Running Grading</Typography>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
