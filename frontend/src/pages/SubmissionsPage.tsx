// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { Box, Chip, Typography, CircularProgress, Select, MenuItem, InputLabel, FormControl } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import { SubmissionStatusChip } from '../components/SubmissionStatusChip';
import { useIsNarrow } from '../components/responsiveColumns';
import { useServerPagination, CHOICE_PAGE_SIZE } from '../components/useServerPagination';

import type { Submission } from '../api';

export function SubmissionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedCourseId = searchParams.get('courseId') || '';

  const { data: courses, isError: coursesFailed, refetch: refetchCourses } = useQuery({
    queryKey: queryKeys.courses.choices,
    queryFn: () => api.getCourses({ size: CHOICE_PAGE_SIZE })
  });

  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.submissions.list(selectedCourseId, params.page, params.size),
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
    field: 'shortCommitSha',
    headerName: 'Submission',
    flex: 1,
    minWidth: 240,
    renderCell: (params: GridRenderCellParams<Submission>) => {
      const row = params.row;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{row.shortCommitSha}</Typography>
            <SubmissionStatusChip status={row.status} />
            {row.late && <Chip size="small" color="warning" label="Late" />}
          </Box>
          {row.commitMessage && (
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{row.commitMessage}</Typography>
          )}
          <Typography variant="caption" color="text.secondary">
            {new Date(row.receivedAt).toLocaleString()} · {row.signatureStatus}
          </Typography>
        </Box>
      );
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
        <Typography variant="h4" component="h1">Submissions</Typography>
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
            {courses?.content.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
          </Select>
        </FormControl>
      </Box>

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
        <Box sx={{ height: 600, width: '100%' }}>
          <DataGrid
            rows={data?.content ?? []}
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
        </Box>
      )}
    </Box>
  );
}
