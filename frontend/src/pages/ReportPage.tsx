// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { Box, Typography, CircularProgress, Button, Paper, Stack } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { useNarrowColumns } from '../components/responsiveColumns';
import type { GridColDef } from '@mui/x-data-grid';

export function ReportPage() {
  const { courseId } = useParams<{ courseId: string }>();

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['report', courseId],
    queryFn: () => api.getCourseReport(courseId || '')
  });

  // Declared before the early returns below: a hook must run on every render.
  const columnVisibilityModel = useNarrowColumns(
    ['studentNumber', 'fullName', 'fullyCompleted', 'partiallyCompleted', 'notStarted', 'completionRate', 'pointsEarned', 'pointsRate', 'submissionCount'],
    ['fullName', 'completionRate', 'pointsRate']
  );

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress /></Box>;
  if (isError) return <QueryErrorNotice message="The course report could not be loaded." onRetry={() => void refetch()} />;
  if (!data) return null;

  const totalStudents = data.students.length;
  const fullyCompleted = data.students.filter(s => s.fullyCompleted === data.totalMandatoryAssignments).length;
  const notStarted = data.students.filter(s => s.submissionCount === 0).length;
  const partiallyCompleted = totalStudents - fullyCompleted - notStarted;

  const columns: GridColDef[] = [
    { field: 'studentNumber', headerName: 'Student No', width: 150 },
    { field: 'fullName', headerName: 'Name', flex: 1, minWidth: 120 },
    { field: 'fullyCompleted', headerName: 'Fully Completed', width: 150 },
    { field: 'partiallyCompleted', headerName: 'Partially Completed', width: 150 },
    { field: 'notStarted', headerName: 'Not Started', width: 150 },
    { field: 'completionRate', headerName: 'Completion', width: 110, valueFormatter: (value: number) => `${(value * 100).toFixed(1)}%` },
    { field: 'pointsEarned', headerName: 'Points', width: 100 },
    { field: 'pointsRate', headerName: 'Points %', width: 100, valueFormatter: (value: number) => `${(value * 100).toFixed(1)}%` },
    { field: 'submissionCount', headerName: 'Submissions', width: 120 }
  ];

  const handleExport = (format: 'csv' | 'json' | 'xlsx') => {
    window.location.href = `/api/v1/reports/courses/${courseId}/export?format=${format}`;
  };

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Course Report</Typography>
      
      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>Metrics</Typography>
        <Typography variant="body1">Completion rate = fully completed mandatory assignments / mandatory assignments</Typography>
        <Typography variant="body1">Points rate = points earned / points available</Typography>
        
        <Box sx={{ mt: 2 }}>
          <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
            Fully completed: {fullyCompleted} of {totalStudents} / Partially completed: {partiallyCompleted} of {totalStudents} / Not started: {notStarted} of {totalStudents}
          </Typography>
        </Box>

        <Stack direction="row" spacing={2} sx={{ mt: 3 }}>
          <Button variant="outlined" onClick={() => handleExport('csv')}>Export CSV</Button>
          <Button variant="outlined" onClick={() => handleExport('json')}>Export JSON</Button>
          <Button variant="outlined" onClick={() => handleExport('xlsx')}>Export XLSX</Button>
        </Stack>
      </Paper>

      <Box sx={{ height: 600, width: '100%' }}>
        <DataGrid
          getRowId={(row: { studentId: string }) => row.studentId}
          rows={data.students}
          columns={columns}
          columnVisibilityModel={columnVisibilityModel}
          initialState={{
            pagination: { paginationModel: { pageSize: 50 } },
          }}
          pageSizeOptions={[50, 100]}
          disableRowSelectionOnClick
        />
      </Box>
    </Box>
  );
}
