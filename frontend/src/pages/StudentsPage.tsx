// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { Box, Typography, CircularProgress, Button } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { StudentStatusChip } from '../components/StudentStatusChip';
import { useIsNarrow } from '../components/responsiveColumns';
import { useServerPagination } from '../components/useServerPagination';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import type { StudentSummary } from '../api';

export function StudentsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const archiveMutation = useMutation({
    mutationFn: (id: string) => api.archiveStudent(id),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: queryKeys.students.list(params.page, params.size).slice(0, 2) }); }
  });
  const restoreMutation = useMutation({
    mutationFn: (id: string) => api.restoreStudent(id),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: queryKeys.students.list(params.page, params.size).slice(0, 2) }); }
  });
  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.students.list(params.page, params.size),
    queryFn: () => api.getStudents(params),
    // Keeps the current rows on screen while the next page loads; without it the row
    // count drops to zero and the grid bounces back to page one.
    placeholderData: (previous) => previous
  });

  // Declared before the early returns below: a hook must run on every render.
  const isNarrow = useIsNarrow();

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress aria-label="Loading students" /></Box>;
  if (isError || !data) {
    return <QueryErrorNotice message="The student list could not be loaded." onRetry={() => void refetch()} />;
  }

  const wideColumns: GridColDef[] = [
    { field: 'studentUsername', headerName: 'Student ID / Username', width: 190 },
    { field: 'firstName', headerName: 'First Name', width: 150 },
    { field: 'lastName', headerName: 'Last Name', width: 150 },
    { field: 'email', headerName: 'Email', flex: 1, minWidth: 220 },
    {
      field: 'status',
      headerName: 'Status',
      width: 190,
      renderCell: (params: GridRenderCellParams<StudentSummary>) => <StudentStatusChip status={params.row.status} />
    },
    {
      // An archived student can only be restored, a live one only archived. Both
      // transitions are confirmations: an accidental click removes a student from the
      // grading pool or, no worse, brings the wrong profile back.
      field: 'actions', headerName: 'Actions', width: 130, sortable: false,
      renderCell: (params: GridRenderCellParams<StudentSummary>) => (
        params.row.status === 'ARCHIVED' ? (
          <Button size="small" disabled={restoreMutation.isPending}
            onClick={() => { if (window.confirm(`Restore ${params.row.firstName} ${params.row.lastName} so they can submit again?`)) restoreMutation.mutate(params.row.id); }}>
            Restore
          </Button>
        ) : (
          <Button size="small" color="error" disabled={archiveMutation.isPending}
            onClick={() => { if (window.confirm(`Archive ${params.row.firstName} ${params.row.lastName}?`)) archiveMutation.mutate(params.row.id); }}>
            Archive
          </Button>
        )
      )
    }
  ];

  /**
   * One stacked cell per student, used instead of columns on a narrow screen.
   *
   * Hiding the name and address would put them out of reach entirely: the student detail
   * route has no content yet, so what the list omits cannot be seen anywhere.
   */
  const narrowColumn: GridColDef = {
    field: 'studentUsername',
    headerName: 'Student ID / Username',
    flex: 1,
    minWidth: 240,
    renderCell: (params: GridRenderCellParams<StudentSummary>) => {
      const row = params.row;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2">{row.firstName} {row.lastName}</Typography>
            <StudentStatusChip status={row.status} />
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {row.studentUsername} · {row.email}
          </Typography>
        </Box>
      );
    }
  };

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Students</Typography>
      <Box sx={{ height: 600, width: '100%', mt: 2 }}>
        <DataGrid
          rows={data.content}
          columns={isNarrow ? [narrowColumn] : wideColumns}
          {...(isNarrow ? { getRowHeight: () => 'auto' as const } : {})}
          paginationMode="server"
          rowCount={data.totalElements}
          paginationModel={paginationModel}
          onPaginationModelChange={setPaginationModel}
          pageSizeOptions={[20, 50, 100]}
          // Only the requested page is in memory, so a client-side sort would silently
          // reorder that page alone while appearing to sort the whole collection.
          disableColumnSorting
          disableRowSelectionOnClick
          onRowClick={(params) => { void navigate(`/students/${params.id}`); }}
        />
      </Box>
    </Box>
  );
}
