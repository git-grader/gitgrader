// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Chip, CircularProgress, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';
import { api } from '../api';
import type { ClassProgressReport } from '../api';
import { fetchBlob, ApiProblem } from '../api/client';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { PageHeader } from '../components/PageHeader';
import { StudentStatusChip } from '../components/StudentStatusChip';

type StudentRow = ClassProgressReport['students'][number];
type ProgressFilter = 'all' | 'not-started' | 'in-progress' | 'complete';

function progressState(student: StudentRow, mandatoryAssignments: number): Exclude<ProgressFilter, 'all'> {
  if (student.submissionCount === 0) return 'not-started';
  if (mandatoryAssignments > 0 && student.fullyCompleted >= mandatoryAssignments) return 'complete';
  return 'in-progress';
}

function isInactive(lastActivityAt: string | null | undefined): boolean {
  return !lastActivityAt || Date.now() - new Date(lastActivityAt).getTime() > 14 * 24 * 60 * 60 * 1000;
}

export function ClassProgressPage() {
  const { courseId = '', classId = '' } = useParams<{ courseId: string; classId: string }>();
  const [search, setSearch] = useState('');
  const [progress, setProgress] = useState<ProgressFilter>('all');
  const [enrollment, setEnrollment] = useState('ALL');
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const query = useQuery({
    queryKey: queryKeys.classReport(courseId, classId),
    queryFn: () => api.getClassReport(courseId, classId),
    enabled: !!courseId && !!classId
  });

  async function exportClass() {
    setExporting(true);
    setExportError(null);
    try {
      const blob = await fetchBlob(
        `/api/v1/reports/courses/${encodeURIComponent(courseId)}/classes/${encodeURIComponent(classId)}/export?format=xlsx`
      );
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'class-report.xlsx';
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
    }
    catch (error) {
      setExportError(error instanceof ApiProblem
        ? `The XLSX export failed (${String(error.status)}).`
        : 'The XLSX export could not be downloaded. Check your connection and try again.');
    }
    finally {
      setExporting(false);
    }
  }

  const students = useMemo(() => {
    const report = query.data;
    if (!report) return [];
    const normalizedSearch = search.trim().toLocaleLowerCase();
    return report.students.filter((student) => {
      const matchesSearch = !normalizedSearch ||
        `${student.fullName} ${student.studentUsername}`.toLocaleLowerCase().includes(normalizedSearch);
      const matchesProgress = progress === 'all' || progressState(student, report.totalMandatoryAssignments) === progress;
      const matchesEnrollment = enrollment === 'ALL' || student.enrollmentStatus === enrollment;
      return matchesSearch && matchesProgress && matchesEnrollment;
    });
  }, [query.data, search, progress, enrollment]);

  if (query.isLoading) return <Box sx={{ p: 4 }}><CircularProgress aria-label="Loading class progress" /></Box>;
  if (query.isError || !query.data) {
    return <QueryErrorNotice message="Class progress could not be loaded." onRetry={() => void query.refetch()} />;
  }

  const report = query.data;
  const columns: GridColDef<StudentRow>[] = [
    {
      field: 'studentUsername', headerName: 'Student ID / Username', width: 190,
      renderCell: ({ row }) => (
        <Link to={`/students/${encodeURIComponent(row.studentId)}?courseId=${encodeURIComponent(courseId)}&classId=${encodeURIComponent(classId)}`}>
          {row.studentUsername}
        </Link>
      )
    },
    { field: 'fullName', headerName: 'Name', flex: 1, minWidth: 160 },
    {
      field: 'enrollmentStatus', headerName: 'Enrollment', width: 130,
      renderCell: ({ row }) => <Chip size="small" label={row.enrollmentStatus} variant="outlined" />
    },
    { field: 'fullyCompleted', headerName: 'Complete', width: 105 },
    { field: 'partiallyCompleted', headerName: 'In progress', width: 110 },
    { field: 'notStarted', headerName: 'Not started', width: 110 },
    {
      field: 'completionRate', headerName: 'Completion', width: 110,
      renderCell: ({ row }) => `${(row.completionRate * 100).toFixed(1)}%`
    },
    {
      field: 'pointsEarned', headerName: 'Points', width: 130,
      renderCell: ({ row }) => `${row.pointsEarned} / ${row.totalPoints}`
    },
    ...report.assignments.map((assignment): GridColDef<StudentRow> => ({
      field: `assignment-${assignment.assignmentKey}`,
      headerName: assignment.title,
      minWidth: 210,
      valueGetter: (_value, row) => row.assignments[assignment.assignmentKey]?.bestPercent ?? -1,
      renderCell: ({ row }) => {
        const result = row.assignments[assignment.assignmentKey];
        if (!result?.latestSubmission) return 'No attempt';
        const latest = result.latestGrading?.scorePercent;
        const latestLabel = latest == null
          ? result.latestGrading?.status.toLocaleLowerCase() ?? 'ungraded'
          : `${latest.toFixed(0)}%`;
        return `${result.bestPercent.toFixed(0)}% best · latest ${latestLabel}`;
      }
    })),
    { field: 'submissionCount', headerName: 'Submissions', width: 120 },
    {
      field: 'lastActivityAt', headerName: 'Last activity', width: 170,
      renderCell: ({ row }) => row.lastActivityAt ? new Date(row.lastActivityAt).toLocaleString() : 'No activity'
    },
    {
      field: 'attention', headerName: 'Attention', width: 150, sortable: false,
      renderCell: ({ row }) => {
        const states = Object.values(row.assignments);
        const hasFailure = states.some((item) => item.latestSubmission?.status === 'FAILED');
        const missing = row.notStarted > 0;
        const inactive = isInactive(row.lastActivityAt);
        return (
          <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {missing && <Chip size="small" color="warning" label="Missing work" />}
            {hasFailure && <Chip size="small" color="error" label="Latest failed" />}
            {inactive && <Chip size="small" variant="outlined" label="Inactive*" />}
          </Stack>
        );
      }
    },
    {
      field: 'status', headerName: 'Student status', width: 145,
      renderCell: ({ row }) => <StudentStatusChip status={row.status} />
    }
  ];

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <PageHeader title={report.className} description={`Class ${report.classKey}`} actions={
        <>
          <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}`}>
            Back to course
          </Button>
        <Button
          variant="outlined"
          startIcon={<FileDownloadOutlinedIcon />}
          onClick={() => void exportClass()}
          disabled={exporting}
        >
          {exporting ? 'Exporting XLSX…' : 'Export XLSX'}
        </Button>
        </>
      } />

      {exportError && <Alert severity="error">{exportError}</Alert>}

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
        <Paper variant="outlined" sx={{ p: 2, flex: 1 }}>
          <Typography variant="h5">{report.students.length}</Typography>
          <Typography color="text.secondary">Enrolled students</Typography>
        </Paper>
        <Paper variant="outlined" sx={{ p: 2, flex: 1 }}>
          <Typography variant="h5">{report.totalMandatoryAssignments}</Typography>
          <Typography color="text.secondary">Mandatory assignments</Typography>
        </Paper>
        <Paper variant="outlined" sx={{ p: 2, flex: 1 }}>
          <Typography variant="h5">{report.totalPointsAvailable}</Typography>
          <Typography color="text.secondary">Points available</Typography>
        </Paper>
      </Stack>

      <Box component="section" aria-labelledby="assignment-results-heading">
        <Typography id="assignment-results-heading" variant="h6" component="h2" sx={{ mb: 1 }}>
          Assignment results
        </Typography>
        {report.assignments.length === 0 ? (
          <Typography color="text.secondary">No assignments are available in this course.</Typography>
        ) : (
          <Stack spacing={1}>
            {report.assignments.map((assignment) => (
              <Paper key={assignment.assignmentId} variant="outlined" sx={{ p: 2 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
                  <Box>
                    <Typography variant="subtitle1">{assignment.title}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {assignment.testCount} tests · average latest graded result {assignment.averagePercent.toFixed(1)}%
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                    <Chip size="small" label={`${assignment.passedCount} passed`} color="success" variant="outlined" />
                    <Chip size="small" label={`${assignment.failedCount} failed`} color="error" variant="outlined" />
                    <Chip size="small" label={`${assignment.infrastructureErrorCount} infrastructure errors`} color="warning" variant="outlined" />
                    <Chip size="small" label={`${assignment.notStartedCount} not started`} />
                  </Stack>
                </Box>
              </Paper>
            ))}
          </Stack>
        )}
      </Box>

      <Box component="section" aria-labelledby="roster-heading">
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2, mb: 1 }}>
          <Typography id="roster-heading" variant="h6" component="h2">Students</Typography>
          <Typography color="text.secondary">{students.length} of {report.students.length} students</Typography>
        </Box>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
          <TextField
            label="Search students"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            size="small"
            sx={{ width: { xs: '100%', sm: 'auto' }, minWidth: { sm: 240 } }}
          />
          <TextField select label="Progress" value={progress} onChange={(event) => setProgress(event.target.value as ProgressFilter)} size="small" sx={{ width: { xs: '100%', sm: 'auto' }, minWidth: { sm: 170 } }}>
            <MenuItem value="all">All progress</MenuItem>
            <MenuItem value="not-started">Not started</MenuItem>
            <MenuItem value="in-progress">In progress</MenuItem>
            <MenuItem value="complete">Complete</MenuItem>
          </TextField>
          <TextField select label="Enrollment" value={enrollment} onChange={(event) => setEnrollment(event.target.value)} size="small" sx={{ width: { xs: '100%', sm: 'auto' }, minWidth: { sm: 170 } }}>
            <MenuItem value="ALL">All enrollments</MenuItem>
            <MenuItem value="ACTIVE">Active</MenuItem>
            <MenuItem value="WITHDRAWN">Withdrawn</MenuItem>
            <MenuItem value="ARCHIVED">Archived</MenuItem>
          </TextField>
        </Stack>
        {report.students.length === 0 ? (
          <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
            <Typography>No students are enrolled in this class yet.</Typography>
          </Paper>
        ) : students.length === 0 ? (
          <Typography color="text.secondary">No students match these filters.</Typography>
        ) : (
          <Box sx={{ width: '100%', overflowX: 'auto' }}>
            <DataGrid
              aria-label="Class roster"
              autoHeight
              rows={students}
              columns={columns}
              getRowId={(row) => row.studentId}
              disableRowSelectionOnClick
              sx={{ minWidth: 720, '& .MuiDataGrid-cell': { alignItems: 'center' } }}
            />
          </Box>
        )}
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          * Inactive means no activity or no activity in the last 14 days.
        </Typography>
      </Box>
    </Box>
  );
}
