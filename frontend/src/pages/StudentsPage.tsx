// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { api, getAllPages } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { PageHeader } from '../components/PageHeader';
import { Box, Typography, CircularProgress, Button, TextField, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { StudentStatusChip } from '../components/StudentStatusChip';
import { useIsNarrow } from '../components/responsiveColumns';
import { useMemo, useState } from 'react';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import type { StudentSummary } from '../api';

const STUDENT_STATUSES = ['', 'SELF_REGISTERED', 'VERIFIED_BY_INSTRUCTOR', 'SUSPENDED', 'ARCHIVED'] as const;

export function StudentsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const archiveMutation = useMutation({
    mutationFn: (id: string) => api.archiveStudent(id),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['students', 'list'] }); }
  });
  const restoreMutation = useMutation({
    mutationFn: (id: string) => api.restoreStudent(id),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['students', 'list'] }); }
  });
  const verifyMutation = useMutation({
    mutationFn: (id: string) => api.verifyStudent(id),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['students', 'list'] }); }
  });
  const suspendMutation = useMutation({
    mutationFn: (id: string) => api.suspendStudent(id),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['students', 'list'] }); }
  });
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.students.list(),
    queryFn: () => getAllPages(api.getStudents, {}, (student) => student.id),
  });

  const students = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return (data ?? []).filter((student) => {
      const matchesSearch = !query || [
        student.studentUsername,
        student.firstName,
        student.lastName,
        student.email,
      ].some((value) => value.toLocaleLowerCase().includes(query));
      return matchesSearch && (!statusFilter || student.status === statusFilter);
    });
  }, [data, search, statusFilter]);

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
      // Verify/suspend map to VERIFIED_BY_INSTRUCTOR/SUSPENDED, archive/restore to
      // ARCHIVED/RESTORE. Archived rows can only be restored; the rest expose the
      // transition that matches their current state.
      field: 'actions', headerName: 'Actions', width: 280, sortable: false,
      renderCell: (params: GridRenderCellParams<StudentSummary>) => {
        const row = params.row;
        if (row.status === 'ARCHIVED') {
          return (
            <Button size="small" variant="outlined" disabled={restoreMutation.isPending}
              onClick={(event) => {
                event.stopPropagation();
                if (window.confirm(`Restore ${row.firstName} ${row.lastName} so they can submit again?`)) restoreMutation.mutate(row.id);
              }}>
              Restore
            </Button>
          );
        }
        return (
          <Box
            sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', alignItems: 'center' }}
            onClick={(event) => event.stopPropagation()}
          >
            {(row.status === 'SELF_REGISTERED') && (
              <Button size="small" variant="outlined" disabled={verifyMutation.isPending}
                onClick={() => verifyMutation.mutate(row.id)}>
                Verify
              </Button>
            )}
            {row.status !== 'SUSPENDED' ? (
              <Button size="small" variant="outlined" color="warning" disabled={suspendMutation.isPending}
                onClick={() => { if (window.confirm(`Suspend ${row.firstName} ${row.lastName}? Pushes will be refused until restored.`)) suspendMutation.mutate(row.id); }}>
                Suspend
              </Button>
            ) : (
              <Button size="small" variant="outlined" disabled={restoreMutation.isPending}
                onClick={() => restoreMutation.mutate(row.id)}>
                Restore
              </Button>
            )}
            <Button size="small" variant="outlined" color="error" disabled={archiveMutation.isPending}
              onClick={() => { if (window.confirm(`Archive ${row.firstName} ${row.lastName}?`)) archiveMutation.mutate(row.id); }}>
              Archive
            </Button>
          </Box>
        );
      }
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
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 0', minHeight: 0, gap: 2 }}>
      <PageHeader title="Students" />
      <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField
          size="small"
          label="Search"
          placeholder="Name, username or email"
          value={search}
          onChange={(e) => { setSearch(e.target.value); }}
          sx={{ minWidth: { xs: '100%', sm: 260 }, flex: '0 1 320px' }}
        />
        <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 200 } }}>
          <InputLabel id="student-status-filter-label">Status Filter</InputLabel>
          <Select
            labelId="student-status-filter-label"
            value={statusFilter}
            label="Status Filter"
            onChange={(e) => { setStatusFilter(e.target.value); }}
          >
            {STUDENT_STATUSES.map((s) => (
              <MenuItem key={s} value={s}>{s === '' ? 'All statuses' : s}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </Box>
      <Box sx={{ flex: '1 1 0', minHeight: { xs: 520, md: 0 }, width: '100%', minWidth: 0 }}>
        <DataGrid
          rows={students}
          columns={isNarrow ? [narrowColumn] : wideColumns}
          {...(isNarrow ? { getRowHeight: () => 'auto' as const } : {})}
          hideFooterPagination
          disableRowSelectionOnClick
          onRowClick={(params) => { void navigate(`/students/${params.id}`); }}
        />
      </Box>
    </Box>
  );
}
