// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Box, Typography, CircularProgress } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';

export function AssignmentsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['assignments'],
    queryFn: () => api.getAssignments()
  });

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress /></Box>;
  if (!data) return null;

  const columns: GridColDef[] = [
    { field: 'assignmentKey', headerName: 'Key', width: 150 },
    { field: 'title', headerName: 'Title', width: 250 },
    { field: 'status', headerName: 'Status', width: 150 },
    { field: 'dueAt', headerName: 'Due At', width: 200 }
  ];

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Assignments</Typography>
      <Box sx={{ height: 600, width: '100%', mt: 2 }}>
        <DataGrid
          rows={data.content}
          columns={columns}
          initialState={{ pagination: { paginationModel: { pageSize: 20 } } }}
          pageSizeOptions={[20, 50, 100]}
          disableRowSelectionOnClick
        />
      </Box>
    </Box>
  );
}
