// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { Alert, Box, Typography, CircularProgress, Button, Paper, Stack } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { useNarrowColumns } from '../components/responsiveColumns';
import type { GridColDef } from '@mui/x-data-grid';
import type { CourseReport } from '../api';

type ExportFormat = 'csv' | 'json' | 'xlsx';

type StudentRow = CourseReport['students'][number];

/**
 * How many students fall in each bucket, without a bucket going negative.
 *
 * The counts were derived by subtracting two of them from the total, and a course with
 * no mandatory assignments satisfies `fullyCompleted === totalMandatoryAssignments` for
 * everyone - including students who have submitted nothing, who were counted as not
 * started as well. The two sets overlapped and the remainder was reported as a negative
 * number of partially completed students.
 */
function buckets(students: readonly StudentRow[], totalMandatory: number) {
  let fullyCompleted = 0;
  let notStarted = 0;
  for (const student of students) {
    if (student.submissionCount === 0) {
      notStarted++;
    }
    else if (totalMandatory > 0 && student.fullyCompleted === totalMandatory) {
      fullyCompleted++;
    }
  }
  return {
    fullyCompleted,
    notStarted,
    partiallyCompleted: students.length - fullyCompleted - notStarted
  };
}

export function ReportPage() {
  const { courseId } = useParams<{ courseId: string }>();
  const [exporting, setExporting] = useState<ExportFormat | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.report(courseId ?? ''),
    queryFn: () => api.getCourseReport(courseId ?? ''),
    enabled: !!courseId
  });

  // Declared before the early returns below: a hook must run on every render.
  const columnVisibilityModel = useNarrowColumns(
    ['studentNumber', 'fullName', 'fullyCompleted', 'partiallyCompleted', 'notStarted', 'completionRate', 'pointsEarned', 'pointsRate', 'submissionCount'],
    ['fullName', 'completionRate', 'pointsRate']
  );

  /**
   * Downloads the export without leaving the application to do it.
   *
   * Assigning `window.location` navigated the browser away, so an expired session or a
   * server error replaced the page with a raw problem document and the instructor lost
   * whatever they were looking at. Fetching it keeps the failure on this page.
   */
  async function handleExport(format: ExportFormat) {
    setExporting(format);
    setExportError(null);
    try {
      const response = await fetch(`/api/v1/reports/courses/${courseId ?? ''}/export?format=${format}`);
      if (!response.ok) {
        setExportError(`The ${format.toUpperCase()} export failed (${String(response.status)}).`);
        return;
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `course-report.${format}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    }
    catch {
      setExportError('The export could not be downloaded. Check your connection and try again.');
    }
    finally {
      setExporting(null);
    }
  }

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress aria-label="Loading course report" /></Box>;
  if (isError || !data) {
    return <QueryErrorNotice message="The course report could not be loaded." onRetry={() => void refetch()} />;
  }

  const totalStudents = data.students.length;
  const counts = buckets(data.students, data.totalMandatoryAssignments);

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

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Course Report</Typography>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>Metrics</Typography>
        <Typography variant="body1">Completion rate = fully completed mandatory assignments / mandatory assignments</Typography>
        <Typography variant="body1">Points rate = points earned / points available</Typography>

        <Box sx={{ mt: 2 }}>
          <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
            Fully completed: {counts.fullyCompleted} of {totalStudents} / Partially completed: {counts.partiallyCompleted} of {totalStudents} / Not started: {counts.notStarted} of {totalStudents}
          </Typography>
          {data.totalMandatoryAssignments === 0 && (
            <Typography variant="body2" color="text.secondary">
              This course has no mandatory assignments, so no student can be counted as fully completed.
            </Typography>
          )}
        </Box>

        {exportError && <Alert severity="error" sx={{ mt: 2 }}>{exportError}</Alert>}

        <Stack direction="row" spacing={2} useFlexGap sx={{ mt: 3, flexWrap: 'wrap' }}>
          {(['csv', 'json', 'xlsx'] as const).map(format => (
            <Button
              key={format}
              variant="outlined"
              disabled={exporting !== null}
              onClick={() => void handleExport(format)}
            >
              {exporting === format ? `Exporting ${format.toUpperCase()}...` : `Export ${format.toUpperCase()}`}
            </Button>
          ))}
        </Stack>
      </Paper>

      <Box sx={{ height: 600, width: '100%' }}>
        <DataGrid
          getRowId={(row: StudentRow) => row.studentId}
          rows={data.students}
          columns={columns}
          columnVisibilityModel={columnVisibilityModel}
          initialState={{
            pagination: { paginationModel: { pageSize: 50 } }
          }}
          pageSizeOptions={[50, 100]}
          disableRowSelectionOnClick
        />
      </Box>
    </Box>
  );
}
