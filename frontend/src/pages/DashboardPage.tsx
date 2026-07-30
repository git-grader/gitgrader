// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Box, Typography, Grid, Paper, CircularProgress } from '@mui/material';

export function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: api.getDashboard
  });

  if (isLoading) return <Box p={4}><CircularProgress /></Box>;
  if (!data) return null;

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Dashboard</Typography>
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
