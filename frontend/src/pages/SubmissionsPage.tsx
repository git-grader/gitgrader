// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, getAllPages } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert } from '../components/MutationErrorAlert';
import { PageHeader } from '../components/PageHeader';
import { Box, Link, Chip, Typography, CircularProgress, Select, MenuItem, InputLabel, FormControl, Alert, Button, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import { SubmissionStatusChip } from '../components/SubmissionStatusChip';
import { useIsNarrow } from '../components/responsiveColumns';
import { useServerPagination } from '../components/useServerPagination';

import type { Submission } from '../api';

export function SubmissionsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedCourseId = searchParams.get('courseId') || '';
  const queryClient = useQueryClient();
  const [retryTarget, setRetryTarget] = useState<Submission | null>(null);
  const retry = useMutation({
    mutationFn: (submissionId: string) => api.regradeSubmission(submissionId),
    onSuccess: () => {
      setRetryTarget(null);
      void queryClient.invalidateQueries({ queryKey: queryKeys.submissions.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
      void queryClient.invalidateQueries({ queryKey: ['report'] });
    }
  });
  const selectedStudentId = searchParams.get('studentId') ?? '';
  const selectedAssignmentId = searchParams.get('assignmentId') ?? '';
  const selectedStatus = searchParams.get('status') || '';

  const { data: courses, isError: coursesFailed, refetch: refetchCourses } = useQuery({
    queryKey: queryKeys.courses.choices,
    queryFn: () => getAllPages(api.getCourses, {}, (course) => course.id)
  });

  const { paginationModel, setPaginationModel, sortModel, setSortModel, params } = useServerPagination();
  const submissionParams = {
    ...params,
    ...(selectedCourseId ? { courseId: selectedCourseId } : {}),
    ...(selectedStatus ? { status: selectedStatus } : {}),
    ...(selectedStudentId ? { studentId: selectedStudentId } : {}),
    ...(selectedAssignmentId ? { assignmentId: selectedAssignmentId } : {})
  };
  const { data, isLoading, isFetching, isError, refetch } = useQuery({
    queryKey: queryKeys.submissions.list(selectedCourseId, params.page, params.size, selectedStatus, params.sort, selectedStudentId, selectedAssignmentId),
    queryFn: () => api.getSubmissions(submissionParams),
    placeholderData: (previous) => previous
  });

  const isNarrow = useIsNarrow();
  function clearScope(field: string) {
    const next = new URLSearchParams(searchParams);
    next.delete(field);
    setSearchParams(next);
    setPaginationModel({ ...paginationModel, page: 0 });
  }
  const infrastructureView = selectedStatus === 'INFRASTRUCTURE_ERROR';
  const runtimeCounts = new Map<string, number>();
  if (infrastructureView) {
    for (const row of data?.content ?? []) {
      const digest = row.runtimeImageDigest ?? '';
      runtimeCounts.set(digest, (runtimeCounts.get(digest) ?? 0) + 1);
    }
  }



  const wideColumns: GridColDef[] = [
    { field: 'studentUsername', headerName: 'Student', width: 150, valueGetter: (_value, row: Submission) => row.studentUsername ?? 'Unknown student' },
    {
      field: 'shortCommitSha', headerName: 'Commit', width: 110, sortable: false,
      renderCell: (params: GridRenderCellParams<Submission>) => (
        <Link component={RouterLink} to={`/submissions/${encodeURIComponent(params.row.id)}`} tabIndex={params.hasFocus ? 0 : -1}>
          {params.row.shortCommitSha}
        </Link>
      )
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 170,
      renderCell: (params: GridRenderCellParams<Submission>) => <SubmissionStatusChip status={params.row.status} />
    },
    // Whether an attempt arrived late and whether its signature verified are the two
    // facts this product exists to record, and the list omitted both because the type it
    // was read through did not mention them.
    {
      field: 'late',
      headerName: 'Late',
      width: 90,
      renderCell: (params: GridRenderCellParams<Submission>) => (
        params.row.late ? <Chip size="small" color="warning" label="Late" /> : null
      )
    },
    {
      field: 'signatureStatus',
      headerName: 'Signature',
      width: 140,
      renderCell: (params: GridRenderCellParams<Submission>) => (
        <Chip
          size="small"
          variant="outlined"
          color={params.row.signatureStatus === 'VERIFIED' ? 'success' : 'default'}
          label={params.row.signatureStatus}
        />
      )
    },
    // The API carries no score on a submission - it belongs to the grading run - so the
    // column that used to sit here could never fill. The commit subject is data the list
    // actually has, and is what identifies an attempt to a human.
    { field: 'commitMessage', headerName: 'Commit message', flex: 1, minWidth: 200 },
    ...(infrastructureView ? [{
      field: 'retry', headerName: 'Recovery', width: 130, sortable: false,
      renderCell: (params: GridRenderCellParams<Submission>) => (
        <Button size="small" variant="outlined" disabled={retry.isPending || isFetching || params.row.status !== 'INFRASTRUCTURE_ERROR'} onClick={(event) => {
          event.stopPropagation(); retry.reset(); setRetryTarget(params.row);
        }}>Retry grading</Button>
      )
    }] : []),
    { field: 'receivedAt', headerName: 'Received At', width: 200, valueGetter: (val: string) => val ? new Date(val).toLocaleString() : '' }
  ];

  /**
   * One stacked cell per attempt, used instead of columns on a narrow screen.
   *
   * Hiding the message and the receive time would put them out of reach entirely: the
   * submission detail route has no content yet, so what the list omits cannot be seen
   * anywhere.
   */
  const narrowColumn: GridColDef = {
    field: 'receivedAt',
    headerName: 'Received At',
    flex: 1,
    minWidth: 240,
    renderCell: (params: GridRenderCellParams<Submission>) => {
      const row = params.row;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{row.studentUsername ?? 'Unknown student'}</Typography>
            <Link component={RouterLink} to={`/submissions/${encodeURIComponent(row.id)}`} tabIndex={params.hasFocus ? 0 : -1} sx={{ fontFamily: 'monospace' }}>
              {row.shortCommitSha}
            </Link>
            <SubmissionStatusChip status={row.status} />
            {row.late && <Chip size="small" color="warning" label="Late" />}
          </Box>
          {row.commitMessage && (
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{row.commitMessage}</Typography>
          )}
          <Typography variant="caption" color="text.secondary">
            {new Date(row.receivedAt).toLocaleString()} · {row.signatureStatus}
          </Typography>
          {infrastructureView && <Button size="small" variant="outlined" disabled={retry.isPending || isFetching || row.status !== 'INFRASTRUCTURE_ERROR'} onClick={(event) => {
            event.stopPropagation(); retry.reset(); setRetryTarget(row);
          }}>Retry grading</Button>}
        </Box>
      );
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 0', minHeight: 0, gap: 3 }}>
      <PageHeader title="Submissions" actions={
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', width: { xs: '100%', sm: 'auto' } }}>
        <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 200 } }}>
          <InputLabel id="submission-course-filter-label">Course Filter</InputLabel>
          <Select
            labelId="submission-course-filter-label"
            value={selectedCourseId}
            label="Course Filter"
            onChange={(e) => {
              // A narrower filter has fewer pages, so staying on the current one would
              // ask for a page that no longer exists and show nothing.
              setPaginationModel({ ...paginationModel, page: 0 });
              // Rebuilt from the current parameters rather than replacing them, which
              // dropped every other parameter in the address.
              const newParams = new URLSearchParams(searchParams);
              if (e.target.value) {
                newParams.set('courseId', e.target.value);
              }
              else {
                newParams.delete('courseId');
              }
              setSearchParams(newParams);
            }}
          >
            <MenuItem value=""><em>All courses</em></MenuItem>
            {courses?.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 180 } }}>
          <InputLabel id="submission-status-filter-label">Status Filter</InputLabel>
          <Select
            labelId="submission-status-filter-label"
            value={selectedStatus}
            label="Status Filter"
            onChange={(e) => {
              setPaginationModel({ ...paginationModel, page: 0 });
              const newParams = new URLSearchParams(searchParams);
              if (e.target.value) {
                newParams.set('status', e.target.value);
              }
              else {
                newParams.delete('status');
              }
              setSearchParams(newParams);
            }}
          >
            <MenuItem value=""><em>All statuses</em></MenuItem>
            {['RECEIVED', 'QUEUED', 'RUNNING', 'PASSED', 'FAILED', 'INFRASTRUCTURE_ERROR', 'CANCELLED', 'REJECTED'].map((s) => (
              <MenuItem key={s} value={s}>{s}</MenuItem>
            ))}
          </Select>
        </FormControl>
        </Box>
      } />

      {(selectedStudentId || selectedAssignmentId) && <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        {selectedStudentId && <Chip label={`Student: ${data?.content.find((row) => row.studentId === selectedStudentId)?.studentUsername ?? selectedStudentId}`} onDelete={() => clearScope('studentId')} />}
        {selectedAssignmentId && <Chip label="Assignment filter" title={selectedAssignmentId} onDelete={() => clearScope('assignmentId')} />}
      </Box>}
      {infrastructureView && <Alert severity="warning">
        {data ? `${data.totalElements} ${data.totalElements === 1 ? 'submission has' : 'submissions have'} infrastructure errors in this selection.` : 'Infrastructure failures are selected.'}
        {' '}Open a submission to inspect its recorded metadata, or retry grading after the platform problem is resolved.
        {' '}The service does not expose detailed infrastructure logs here.
      </Alert>}
      {infrastructureView && runtimeCounts.size > 0 && <Box component="section" aria-label="Runtime images on this page">
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>Runtime images on this page</Typography>
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
          {Array.from(runtimeCounts, ([digest, count]) => <Chip key={digest} title={digest || 'No runtime image recorded'}
            label={`${digest ? `${digest.slice(0, 14)}…${digest.slice(-7)}` : 'No runtime image recorded'}: ${count}`} variant="outlined" sx={{ maxWidth: '100%' }} />)}
        </Box>
      </Box>}
      {retry.isSuccess && <Alert severity="success">Grading retry queued.</Alert>}
      {coursesFailed && (
        <QueryErrorNotice
          message="The course list could not be loaded, so submissions cannot be filtered by course."
          onRetry={() => void refetchCourses()}
        />
      )}

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress aria-label="Loading submissions" />
        </Box>
      ) : isError ? (
        <QueryErrorNotice message="The submissions could not be loaded." onRetry={() => void refetch()} />
      ) : (
        <Box component="section" aria-label="Submission results" sx={{ height: { xs: 520, md: 600 }, flexShrink: 0, width: '100%', minWidth: 0 }}>
          <DataGrid
            loading={isFetching}
            rows={data?.content ?? []}
            columns={isNarrow ? [narrowColumn] : wideColumns}
            {...(isNarrow ? { getRowHeight: () => 'auto' as const } : {})}
            paginationMode="server"
            rowCount={data?.totalElements ?? 0}
            paginationModel={paginationModel}
            onPaginationModelChange={setPaginationModel}
            sortingMode="server"
            sortModel={sortModel}
            onSortModelChange={setSortModel}
            pageSizeOptions={[20, 50, 100]}
            disableRowSelectionOnClick
            onRowClick={(params) => { void navigate(`/submissions/${encodeURIComponent(String(params.id))}`); }}
          />
        </Box>
      )}
      <Dialog open={retryTarget !== null} onClose={() => { if (!retry.isPending) { setRetryTarget(null); retry.reset(); } }} fullWidth maxWidth="sm">
        <DialogTitle>Retry grading?</DialogTitle>
        <DialogContent>
          <Typography sx={{ overflowWrap: 'anywhere' }}>Queue another grading run for {retryTarget?.studentUsername ?? 'this student'}, commit {retryTarget?.shortCommitSha}?</Typography>
          <MutationErrorAlert error={retry.error} sx={{ mt: 2 }} />
        </DialogContent>
        <DialogActions>
          <Button disabled={retry.isPending} onClick={() => { setRetryTarget(null); retry.reset(); }}>Cancel</Button>
          <Button variant="contained" disabled={retry.isPending} onClick={() => { if (retryTarget) retry.mutate(retryTarget.id); }}>
            {retry.isPending ? 'Queueing…' : 'Queue retry'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
