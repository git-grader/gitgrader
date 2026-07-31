// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Typography, CircularProgress, Select, MenuItem, InputLabel, FormControl } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';

export function SubmissionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedCourseId = searchParams.get('courseId') || '';

  const { data: courses } = useQuery({ queryKey: ['courses'], queryFn: () => api.getCourses() });

  const { data, isLoading } = useQuery({
    queryKey: ['submissions', selectedCourseId],
    queryFn: () => api.getSubmissions(selectedCourseId ? { courseId: selectedCourseId } : undefined)
  });

  const columns: GridColDef[] = [
    { field: 'commitSha', headerName: 'Commit', width: 150, valueGetter: (val: string) => val ? val.substring(0, 8) : '' },
    { field: 'status', headerName: 'Status', width: 150 },
    { field: 'score', headerName: 'Score', width: 100 },
    { field: 'receivedAt', headerName: 'Received At', width: 200, valueGetter: (val: string) => val ? new Date(val).toLocaleString() : '' }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h4" component="h1">Submissions</Typography>
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel>Course Filter</InputLabel>
          <Select
            value={selectedCourseId}
            label="Course Filter"
            onChange={(e) => setSearchParams(e.target.value ? { courseId: e.target.value } : {})}
          >
            <MenuItem value=""><em>All courses</em></MenuItem>
            {courses?.content.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
          </Select>
        </FormControl>
      </div>

      {isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '32px' }}><CircularProgress /></div>
      ) : (
        <div style={{ height: 600, width: '100%' }}>
          <DataGrid
            rows={data?.content || []}
            columns={columns}
            initialState={{ pagination: { paginationModel: { pageSize: 20 } } }}
            pageSizeOptions={[20, 50, 100]}
            disableRowSelectionOnClick
          />
        </div>
      )}
    </div>
  );
}
