// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Box, Typography, CircularProgress } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { StudentStatusChip } from '../components/StudentStatusChip';
import { useIsNarrow } from '../components/responsiveColumns';
import { useServerPagination } from '../components/useServerPagination';
import type { GridColDef } from '@mui/x-data-grid';

interface StudentRow {
  readonly studentNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  readonly status: string;
}

export function StudentsPage() {
  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading } = useQuery({
    queryKey: ['students', params.page, params.size],
    queryFn: () => api.getStudents(params),
    // Keeps the current rows on screen while the next page loads; without it the row
    // count drops to zero and the grid bounces back to page one.
    placeholderData: (previous) => previous
  });

  // Declared before the early returns below: a hook must run on every render.
  const isNarrow = useIsNarrow();

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress /></Box>;
  if (!data) return null;

  const wideColumns: GridColDef[] = [
    { field: 'studentNumber', headerName: 'Student No', width: 150 },
    { field: 'firstName', headerName: 'First Name', width: 150 },
    { field: 'lastName', headerName: 'Last Name', width: 150 },
    { field: 'email', headerName: 'Email', flex: 1, minWidth: 220 },
    {
      field: 'status',
      headerName: 'Status',
      width: 190,
      renderCell: (params) => <StudentStatusChip status={String(params.value)} />
    }
  ];

  /**
   * One stacked cell per student, used instead of columns on a narrow screen.
   *
   * Hiding the name and address would put them out of reach entirely: there is no
   * student detail page to open, so what the list omits cannot be seen anywhere.
   */
  const narrowColumn: GridColDef = {
    field: 'studentNumber',
    headerName: 'Student',
    flex: 1,
    minWidth: 240,
    renderCell: (params) => {
      const row = params.row as StudentRow;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2">{row.firstName} {row.lastName}</Typography>
            <StudentStatusChip status={row.status} />
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {row.studentNumber} · {row.email}
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
        />
      </Box>
    </Box>
  );
}
