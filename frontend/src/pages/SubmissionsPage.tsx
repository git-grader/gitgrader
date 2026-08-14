// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { Box, Typography, CircularProgress, Select, MenuItem, InputLabel, FormControl } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';
import { SubmissionStatusChip } from '../components/SubmissionStatusChip';
import { useIsNarrow } from '../components/responsiveColumns';
import { useServerPagination, CHOICE_PAGE_SIZE } from '../components/useServerPagination';

interface SubmissionRow {
  readonly shortCommitSha: string;
  readonly status: string;
  readonly commitMessage?: string | null;
  readonly receivedAt: string;
}

export function SubmissionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedCourseId = searchParams.get('courseId') || '';

  const { data: courses } = useQuery({
    queryKey: ['courses', 'choices'],
    queryFn: () => api.getCourses({ size: CHOICE_PAGE_SIZE })
  });

  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['submissions', selectedCourseId, params.page, params.size],
    queryFn: () => api.getSubmissions(selectedCourseId ? { ...params, courseId: selectedCourseId } : params),
    placeholderData: (previous) => previous
  });

  const isNarrow = useIsNarrow();

  const wideColumns: GridColDef[] = [
    { field: 'shortCommitSha', headerName: 'Commit', width: 110 },
    {
      field: 'status',
      headerName: 'Status',
      width: 170,
      renderCell: (params) => <SubmissionStatusChip status={String(params.value)} />
    },
    // The API carries no score on a submission - it belongs to the grading run - so the
    // column that used to sit here could never fill. The commit subject is data the list
    // actually has, and is what identifies an attempt to a human.
    { field: 'commitMessage', headerName: 'Commit message', flex: 1, minWidth: 200 },
    { field: 'receivedAt', headerName: 'Received At', width: 200, valueGetter: (val: string) => val ? new Date(val).toLocaleString() : '' }
  ];

  /**
   * One stacked cell per attempt, used instead of columns on a narrow screen.
   *
   * Hiding the message and the receive time would put them out of reach entirely: there
   * is no submission detail page to open, so what the list omits cannot be seen anywhere.
   */
  const narrowColumn: GridColDef = {
    field: 'shortCommitSha',
    headerName: 'Submission',
    flex: 1,
    minWidth: 240,
    renderCell: (params) => {
      const row = params.row as SubmissionRow;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{row.shortCommitSha}</Typography>
            <SubmissionStatusChip status={row.status} />
          </Box>
          {row.commitMessage && (
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{row.commitMessage}</Typography>
          )}
          <Typography variant="caption" color="text.secondary">
            {new Date(row.receivedAt).toLocaleString()}
          </Typography>
        </Box>
      );
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
        <Typography variant="h4" component="h1">Submissions</Typography>
        <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 200 } }}>
          <InputLabel>Course Filter</InputLabel>
          <Select
            value={selectedCourseId}
            label="Course Filter"
            onChange={(e) => {
              // A narrower filter has fewer pages, so staying on the current one would
              // ask for a page that no longer exists and show nothing.
              setPaginationModel({ ...paginationModel, page: 0 });
              setSearchParams(e.target.value ? { courseId: e.target.value } : {});
            }}
          >
            <MenuItem value=""><em>All courses</em></MenuItem>
            {courses?.content.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
          </Select>
        </FormControl>
      </div>

      {isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '32px' }}><CircularProgress /></div>
      ) : isError ? (
        <QueryErrorNotice message="The submissions could not be loaded." onRetry={() => void refetch()} />
      ) : (
        <div style={{ height: 600, width: '100%' }}>
          <DataGrid
            rows={data?.content || []}
            columns={isNarrow ? [narrowColumn] : wideColumns}
            {...(isNarrow ? { getRowHeight: () => 'auto' as const } : {})}
            paginationMode="server"
            rowCount={data?.totalElements ?? 0}
            paginationModel={paginationModel}
            onPaginationModelChange={setPaginationModel}
            // Only the requested page is in memory, so a client-side sort would silently
            // reorder that page alone while appearing to sort the whole collection.
            disableColumnSorting
            pageSizeOptions={[20, 50, 100]}
            disableRowSelectionOnClick
          />
        </div>
      )}
    </div>
  );
}
