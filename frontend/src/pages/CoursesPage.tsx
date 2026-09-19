// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams, Link } from 'react-router';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import { useServerPagination } from '../components/useServerPagination';
import { useIsNarrow } from '../components/responsiveColumns';
import { CourseStatusChip } from '../components/CourseStatusChip';
import { fromZonedInputValue } from '../components/localDateTime';
import type { CourseView, CourseDefinition } from '../api';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import { Box, Chip, Typography, CircularProgress, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControlLabel, Switch, FormControl, InputLabel, Select, MenuItem } from '@mui/material';

const COURSE_STATUSES = ['DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED'] as const;

const emptyForm = (): Partial<CourseDefinition> => ({
  courseKey: '',
  name: '',
  description: '',
  semester: '',
  startsOn: '',
  endsOn: '',
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  status: 'DRAFT',
  registrationOpensAt: '',
  registrationClosesAt: '',
  registrationEnabled: false
});

export function CoursesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedStatus = searchParams.get('status');
  // An address can name a status the filter has no option for, and MUI renders a Select
  // whose value is out of range as an empty box with no way to tell what it is showing.
  const statusFilter = COURSE_STATUSES.find(candidate => candidate === requestedStatus) ?? 'ACTIVE';
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Partial<CourseDefinition>>(emptyForm);

  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.courses.list(statusFilter, params.page, params.size),
    queryFn: () => api.getCourses({ ...params, status: statusFilter }),
    // Keeps the current rows on screen while the next page loads; without it the row
    // count drops to zero and the grid bounces back to page one.
    placeholderData: (previous) => previous
  });

  function showStatus(status: string) {
    // Creating a course also switches the status filter, so it needs the same page
    // reset the filter control does. Landing on page 3 of a status that has one page
    // shows "No courses found" for a course that was just created successfully, and
    // the empty branch renders no pager to get back with.
    setPaginationModel({ ...paginationModel, page: 0 });
    const newParams = new URLSearchParams(searchParams);
    newParams.set('status', status);
    setSearchParams(newParams);
  }

  const createMutation = useMutation({
    mutationFn: (req: CourseDefinition) => api.createCourse(req),
    // The created course is in the response, so the status to switch to is the one the
    // server actually stored rather than the one the form asked for.
    onSuccess: (created) => {
      showStatus(created.status);
      void queryClient.invalidateQueries({ queryKey: queryKeys.courses.all });
      closeDialog();
    }
  });

  // Declared before the early returns below: a hook must run on every render.
  const isNarrow = useIsNarrow();

  function closeDialog() {
    setOpen(false);
    setForm(emptyForm());
    createMutation.reset();
  }

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();

    const toEmptyNull = (val?: string | null) => (val && val.trim() !== '') ? val : null;
    // The registration window is stated in the course's own timezone, not the reader's.
    // Converting through the browser stored a window hours away from the one typed, and
    // `new Date('').toISOString()` threw out of this handler for anything unparseable,
    // taking the filled-in form with it.
    const zone = form.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone;

    createMutation.mutate({
      // Repository paths use lowercase course keys; accept natural uppercase input.
      courseKey: (form.courseKey ?? '').trim().toLowerCase(),
      name: form.name ?? '',
      description: toEmptyNull(form.description),
      semester: toEmptyNull(form.semester),
      startsOn: toEmptyNull(form.startsOn),
      endsOn: toEmptyNull(form.endsOn),
      timezone: zone,
      status: form.status ?? 'DRAFT',
      registrationOpensAt: fromZonedInputValue(form.registrationOpensAt, zone),
      registrationClosesAt: fromZonedInputValue(form.registrationClosesAt, zone),
      registrationEnabled: form.registrationEnabled ?? false
    });
  };

  const fieldErrors = problemFieldErrors(createMutation.error);

  const wideColumns: GridColDef[] = [
    {
      field: 'name',
      headerName: 'Name',
      flex: 1,
      minWidth: 200,
      renderCell: (params: GridRenderCellParams<CourseView>) => (
        <Link to={`/courses/${params.row.id}`} style={{ color: 'inherit' }}>{params.row.name}</Link>
      )
    },
    { field: 'courseKey', headerName: 'Key', width: 150, valueGetter: (_value: string, row: CourseView) => row.courseKey },
    {
      field: 'status',
      headerName: 'Status',
      width: 130,
      renderCell: (params: GridRenderCellParams<CourseView>) => <CourseStatusChip status={params.row.status} />
    },
    {
      field: 'semester',
      headerName: 'Semester',
      width: 130,
      valueGetter: (_value: string, row: CourseView) => row.semester ?? '—'
    },
    {
      field: 'registrationEnabled',
      headerName: 'Registration',
      width: 140,
      renderCell: (params: GridRenderCellParams<CourseView>) => (
        params.row.registrationEnabled
          ? <Chip size="small" color="success" label="Open" />
          : <Chip size="small" variant="outlined" label="Closed" />
      )
    },
    {
      field: 'actions', headerName: 'Actions', width: 100, sortable: false,
      renderCell: (params: GridRenderCellParams<CourseView>) => (
        <Button size="small" component={Link} to={`/courses/${params.row.id}`}>Open</Button>
      )
    }
  ];

  /**
   * One stacked cell per course, used instead of columns on a narrow screen.
   *
   * Hiding the key, the semester, and the registration state would put them out of reach
   * on a phone: the course detail route exists, but the summary that states whether new
   * students can sign up belongs on the list. Stacking keeps every field reachable
   * without a horizontal scroll.
   */
  const narrowColumn: GridColDef = {
    field: 'name',
    headerName: 'Course',
    flex: 1,
    minWidth: 240,
    renderCell: (params: GridRenderCellParams<CourseView>) => {
      const row = params.row;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2" component={Link} to={`/courses/${row.id}`} sx={{ color: 'inherit', textDecoration: 'none' }}>{row.name}</Typography>
            <CourseStatusChip status={row.status} />
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {row.courseKey} · {row.semester ?? 'no semester'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Registration: {row.registrationEnabled ? 'Open' : 'Closed'}
          </Typography>
        </Box>
      );
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
        <Typography variant="h4" component="h1">Courses</Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', flex: '1 1 auto', justifyContent: 'flex-end' }}>
          <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 200 } }}>
            <InputLabel id="course-status-filter-label">Status Filter</InputLabel>
            <Select
              labelId="course-status-filter-label"
              value={statusFilter}
              label="Status Filter"
              // A narrower filter has fewer pages, so staying on the current one would
              // ask for a page that no longer exists and show nothing.
              onChange={(e) => { showStatus(e.target.value); }}
            >
              {COURSE_STATUSES.map(status => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </Select>
          </FormControl>
          <Button variant="contained" onClick={() => setOpen(true)}>New Course</Button>
        </Box>
      </Box>

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress aria-label="Loading courses" />
        </Box>
      ) : isError ? (
        <QueryErrorNotice message="The courses could not be loaded." onRetry={() => void refetch()} />
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
            pageSizeOptions={[20, 50, 100]}
            // Only the requested page is in memory, so a client-side sort would silently
            // reorder that page alone while appearing to sort the whole collection.
            disableColumnSorting
            disableRowSelectionOnClick
            onRowClick={(params) => { void navigate(`/courses/${params.id}`); }}
          />
        </Box>
      )}

      <Dialog open={open} onClose={() => !createMutation.isPending && closeDialog()} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit}>
          <DialogTitle>New Course</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
            <MutationErrorAlert error={createMutation.error} />
            <TextField
              label="Course Key"
              required
              fullWidth
              value={form.courseKey ?? ''}
              onChange={e => setForm({ ...form, courseKey: e.target.value })}
              error={!!fieldErrors['courseKey']}
              helperText={fieldErrors['courseKey']}
              disabled={createMutation.isPending}
            />
            <TextField
              label="Name"
              required
              fullWidth
              value={form.name ?? ''}
              onChange={e => setForm({ ...form, name: e.target.value })}
              error={!!fieldErrors['name']}
              helperText={fieldErrors['name']}
              disabled={createMutation.isPending}
            />
            <TextField
              label="Description"
              fullWidth
              multiline
              rows={2}
              value={form.description ?? ''}
              onChange={e => setForm({ ...form, description: e.target.value })}
              error={!!fieldErrors['description']}
              helperText={fieldErrors['description']}
              disabled={createMutation.isPending}
            />

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField
                label="Semester"
                fullWidth
                value={form.semester ?? ''}
                onChange={e => setForm({ ...form, semester: e.target.value })}
                error={!!fieldErrors['semester']}
                helperText={fieldErrors['semester']}
                disabled={createMutation.isPending}
              />
              <TextField
                label="Timezone"
                required
                fullWidth
                value={form.timezone ?? ''}
                onChange={e => setForm({ ...form, timezone: e.target.value })}
                error={!!fieldErrors['timezone']}
                helperText={fieldErrors['timezone'] ?? 'The zone the registration window below is stated in.'}
                disabled={createMutation.isPending}
              />
            </Box>

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Starts On" type="date" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.startsOn ?? ''} onChange={e => setForm({ ...form, startsOn: e.target.value })} error={!!fieldErrors['startsOn']} helperText={fieldErrors['startsOn']} disabled={createMutation.isPending} />
              <TextField label="Ends On" type="date" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.endsOn ?? ''} onChange={e => setForm({ ...form, endsOn: e.target.value })} error={!!fieldErrors['endsOn']} helperText={fieldErrors['endsOn']} disabled={createMutation.isPending} />
            </Box>

            <TextField
              select
              label="Status"
              required
              fullWidth
              value={form.status ?? ''}
              onChange={e => setForm({ ...form, status: e.target.value })}
              error={!!fieldErrors['status']}
              helperText={fieldErrors['status'] ?? 'Only an ACTIVE course accepts student registrations.'}
              disabled={createMutation.isPending}
            >
              {COURSE_STATUSES.map(status => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </TextField>

            <FormControlLabel
              control={<Switch checked={form.registrationEnabled ?? false} onChange={e => setForm({ ...form, registrationEnabled: e.target.checked })} disabled={createMutation.isPending} />}
              label="Registration Enabled"
            />

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Registration Opens At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.registrationOpensAt ?? ''} onChange={e => setForm({ ...form, registrationOpensAt: e.target.value })} error={!!fieldErrors['registrationOpensAt']} helperText={fieldErrors['registrationOpensAt']} disabled={createMutation.isPending} />
              <TextField label="Registration Closes At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.registrationClosesAt ?? ''} onChange={e => setForm({ ...form, registrationClosesAt: e.target.value })} error={!!fieldErrors['registrationClosesAt']} helperText={fieldErrors['registrationClosesAt']} disabled={createMutation.isPending} />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={closeDialog} disabled={createMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating...' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
}