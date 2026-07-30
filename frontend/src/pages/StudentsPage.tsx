// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Box, Typography, CircularProgress } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';

export function StudentsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['students'],
    queryFn: () => api.getStudents()
  });

  if (isLoading) return <Box p={4}><CircularProgress /></Box>;
  if (!data) return null;

  const columns: GridColDef[] = [
    { field: 'studentNumber', headerName: 'Student No', width: 150 },
    { field: 'firstName', headerName: 'First Name', width: 150 },
    { field: 'lastName', headerName: 'Last Name', width: 150 },
    { field: 'email', headerName: 'Email', width: 250 },
    { field: 'status', headerName: 'Status', width: 150 }
  ];

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Students</Typography>
      <Box sx={{ height: 600, width: '100%', mt: 2 }}>
        <DataGrid
          rows={data.content}
          columns={columns}
          initialState={{
            pagination: { paginationModel: { pageSize: 20 } },
          }}
          pageSizeOptions={[20, 50, 100]}
          disableRowSelectionOnClick
        />
      </Box>
    </Box>
  );
}
