// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { ApiProblem, fetchBlob } from '../api/client';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { PageHeader } from '../components/PageHeader';
import { Alert, Box, Typography, CircularProgress, Button, Paper, Stack } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { useIsNarrow } from '../components/responsiveColumns';
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
  const isNarrow = useIsNarrow();

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
      const blob = await fetchBlob(
        `/api/v1/reports/courses/${encodeURIComponent(courseId ?? '')}/export?format=${encodeURIComponent(format)}`
      );
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `course-report.${format}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    }
    catch (err) {
      if (err instanceof ApiProblem) {
        setExportError(`The ${format.toUpperCase()} export failed (${String(err.status)}).`);
      }
      else {
        setExportError('The export could not be downloaded. Check your connection and try again.');
      }
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

  const columns: GridColDef<StudentRow>[] = [
    { field: 'studentUsername', headerName: 'Student ID / Username', width: 190 },
    { field: 'fullName', headerName: 'Name', flex: 1, minWidth: 120 },
    { field: 'fullyCompleted', headerName: 'Fully Completed', width: 150 },
    { field: 'partiallyCompleted', headerName: 'Partially Completed', width: 150 },
    { field: 'notStarted', headerName: 'Not Started', width: 150 },
    { field: 'completionRate', headerName: 'Completion %', width: 125, valueFormatter: (value: number) => `${(value * 100).toFixed(1)}%` },
    { field: 'pointsEarned', headerName: 'Points earned', width: 125 },
    { field: 'pointsRate', headerName: 'Points earned %', width: 145, valueFormatter: (value: number) => `${(value * 100).toFixed(1)}%` },
    { field: 'submissionCount', headerName: 'Submissions', width: 120 },
    {
      field: 'lastActivityAt', headerName: 'Last activity', width: 190,
      valueGetter: (value: string | null | undefined) => value ? new Date(value).toLocaleString() : 'No activity'
    },
    ...Array.from(new Set(data.students.flatMap((student) => Object.keys(student.assignments)))).map((assignmentKey): GridColDef<StudentRow> => ({
      field: `assignment:${assignmentKey}`,
      headerName: assignmentKey,
      description: 'Best graded result, shown as percentage and points.',
      width: 180,
      valueGetter: (_value, row) => row.assignments[assignmentKey]?.percent ?? null,
      renderCell: ({ row }) => {
        const assignment = row.assignments[assignmentKey];
        return assignment ? `${assignment.percent}% · ${assignment.points} points` : '—';
      }
    }))
  ];

  const narrowColumn: GridColDef<StudentRow> = {
    field: 'studentUsername',
    headerName: 'Student report',
    flex: 1,
    minWidth: 0,
    renderCell: ({ row }) => (
      <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, width: '100%', minWidth: 0 }}>
        <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{row.fullName}</Typography>
        <Typography variant="caption" color="text.secondary">{row.studentUsername}</Typography>
        <Typography variant="body2">
          Fully completed: {row.fullyCompleted} · Partially completed: {row.partiallyCompleted} · Not started: {row.notStarted}
        </Typography>
        <Typography variant="body2">
          Completion: {(row.completionRate * 100).toFixed(1)}% · Points: {row.pointsEarned} ({(row.pointsRate * 100).toFixed(1)}%) · Submissions: {row.submissionCount}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Last activity: {row.lastActivityAt ? new Date(row.lastActivityAt).toLocaleString() : 'No activity'}
        </Typography>
        {Object.entries(row.assignments).map(([assignmentKey, result]) => (
          <Typography key={assignmentKey} variant="body2" sx={{ overflowWrap: 'anywhere' }}>
            {assignmentKey}: {result.percent}% · {result.points} points
          </Typography>
        ))}
      </Box>
    )
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 0', minHeight: 0, gap: 3, minWidth: 0 }}>
      <PageHeader title="Course Report" />

      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
        <Typography variant="h6" gutterBottom>Metrics</Typography>
        <Typography variant="body1">Completion % = fully completed mandatory assignments / mandatory assignments</Typography>
        <Typography variant="body1">Points earned % = points earned / points available</Typography>
        <Typography variant="body2" color="text.secondary">Assignment results show the best graded attempt as percentage and points.</Typography>

        <Box sx={{ mt: 2 }}>
          <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
            Fully completed: {counts.fullyCompleted} of {totalStudents} / Partially completed: {counts.partiallyCompleted} of {totalStudents} / Not started: {counts.notStarted} of {totalStudents}
          </Typography>
          {data.totalMandatoryAssignments === 0 && (
            <Typography variant="body2" color="text.secondary">
              This course has no mandatory assignments, so no student can be counted as fully completed.
            </Typography>
          )}
          {data.totalPointsAvailable === 0 && (
            <Typography variant="body2" color="text.secondary">
              No assignment points are available in this course report.
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

      {data.students.length === 0 ? (
        <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <Typography>No enrolled students are included in this course report.</Typography>
        </Paper>
      ) : (
        <Box sx={{ height: { xs: 520, md: 600 }, flexShrink: 0, width: '100%', minWidth: 0, overflow: 'hidden' }}>
          <DataGrid
            getRowId={(row: StudentRow) => row.studentId}
            rows={data.students}
            columns={isNarrow ? [narrowColumn] : columns}
            {...(isNarrow ? { getRowHeight: () => 'auto' as const } : {})}
            disableRowSelectionOnClick
            sx={{ minWidth: 0, width: '100%' }}
          />
        </Box>
      )}
    </Box>
  );
}
