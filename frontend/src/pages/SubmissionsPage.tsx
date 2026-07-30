// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Box, Typography, CircularProgress } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';

export function SubmissionsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['submissions'],
    queryFn: () => api.getSubmissions()
  });

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress /></Box>;
  if (!data) return null;

  const columns: GridColDef[] = [
    { field: 'commitSha', headerName: 'Commit', width: 150, valueFormatter: (val: string) => val.substring(0, 8) },
    { field: 'status', headerName: 'Status', width: 150 },
    { field: 'score', headerName: 'Score', width: 100 },
    { field: 'receivedAt', headerName: 'Received At', width: 200, valueFormatter: (val: string) => new Date(val).toLocaleString() }
  ];

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Submissions</Typography>
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
