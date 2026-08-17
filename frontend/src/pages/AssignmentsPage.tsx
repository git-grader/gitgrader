// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState, useMemo } from 'react';
import { useSearchParams, Link as RouterLink } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAssignmentMaterials } from '../hooks/useAssignmentMaterials';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import { fromZonedInputValue } from '../components/localDateTime';
import { numberInputValue, parseNumberInput } from '../components/numberInput';
import type { AssignmentDefinition, AssignmentDetail } from '../api';
import { Box, Link, Typography, CircularProgress, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Select, MenuItem, InputLabel, FormControl, FormControlLabel, Checkbox, Switch, FormHelperText, Tooltip } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { AssignmentStatusChip } from '../components/AssignmentStatusChip';
import { useNarrowColumns } from '../components/responsiveColumns';
import { useServerPagination, CHOICE_PAGE_SIZE } from '../components/useServerPagination';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';

const ASSIGNMENT_STATUSES = ['DRAFT', 'SCHEDULED', 'OPEN', 'CLOSED', 'ARCHIVED'] as const;

/**
 * The form's own shape, where every field may be absent.
 *
 * `Partial` is not enough under `exactOptionalPropertyTypes`: it makes a field optional
 * but still refuses an explicit `undefined`, which is exactly what a cleared number
 * field now produces.
 */
type AssignmentForm = { [K in keyof AssignmentDefinition]?: AssignmentDefinition[K] | undefined };

const emptyForm = (): AssignmentForm => ({
  assignmentKey: '', title: '', description: '', displayOrder: 10, status: 'DRAFT', mandatory: true,
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone, maxPoints: 10, testCount: 0, passThreshold: 0,
  allowLate: false, networkEnabled: false, timeoutSeconds: null, memoryLimitBytes: null, cpuLimit: null,
  pidLimit: null, templateVersionId: null, testSuiteVersionId: null, runtimeId: null
});

export function AssignmentsPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedCourseId = searchParams.get('courseId') ?? '';

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<AssignmentForm>(emptyForm);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const {
    data: courses,
    isError: coursesFailed,
    refetch: refetchCourses
  } = useQuery({
    queryKey: queryKeys.courses.choices,
    queryFn: () => api.getCourses({ size: CHOICE_PAGE_SIZE })
  });
  const materials = useAssignmentMaterials();

  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.assignments.list(selectedCourseId, params.page, params.size),
    queryFn: () => api.getAssignments(selectedCourseId ? { ...params, courseId: selectedCourseId } : params),
    placeholderData: (previous) => previous
  });

  const createMutation = useMutation({
    mutationFn: (req: AssignmentDefinition) => api.createAssignment(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.assignments.all });
      closeDialog();
    }
  });

  function closeDialog() {
    setOpen(false);
    setForm(emptyForm());
    setFieldErrors({});
    createMutation.reset();
  }

  function selectCourse(courseId: string) {
    setPaginationModel({ ...paginationModel, page: 0 });
    // Rebuilt from the current parameters rather than replacing them: assigning a fresh
    // object dropped every other parameter in the address.
    const newParams = new URLSearchParams(searchParams);
    if (courseId) {
      newParams.set('courseId', courseId);
    }
    else {
      newParams.delete('courseId');
    }
    setSearchParams(newParams);
  }

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();

    // A cleared number field reads as absent, not as zero. Coercing it with `|| 0` made
    // an emptied "Max Points" create an assignment worth nothing, silently.
    const errors: Record<string, string> = {};
    if (!form.courseId) errors['courseId'] = 'Choose the course this assignment belongs to.';
    if (!form.assignmentKey) errors['assignmentKey'] = 'Required';
    if (!form.title) errors['title'] = 'Required';
    if (form.maxPoints === undefined) errors['maxPoints'] = 'Required';
    if (form.passThreshold === undefined) errors['passThreshold'] = 'Required';
    if (form.testCount === undefined) errors['testCount'] = 'Required';
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});

    const toEmptyNull = (val?: string | null) => (val && val.trim() !== '') ? val : null;
    // The deadline is stated in the assignment's own timezone. Converting it through the
    // browser's stored an instant hours from the one typed, and since `dueAt` decides
    // whether a submission is late, that cut students off at the wrong moment.
    const zone = form.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone;

    createMutation.mutate({
      courseId: form.courseId ?? '',
      assignmentKey: form.assignmentKey ?? '',
      title: form.title ?? '',
      description: toEmptyNull(form.description),
      status: form.status ?? 'DRAFT',
      timezone: toEmptyNull(zone),
      displayOrder: form.displayOrder ?? 10,
      mandatory: form.mandatory ?? true,
      maxPoints: form.maxPoints ?? 0,
      testCount: form.testCount ?? 0,
      passThreshold: form.passThreshold ?? 0,
      allowLate: form.allowLate ?? false,
      opensAt: fromZonedInputValue(form.opensAt, zone),
      dueAt: fromZonedInputValue(form.dueAt, zone),
      networkEnabled: form.networkEnabled ?? false,
      timeoutSeconds: form.timeoutSeconds ?? null,
      memoryLimitBytes: form.memoryLimitBytes ?? null,
      cpuLimit: form.cpuLimit ?? null,
      pidLimit: form.pidLimit ?? null,
      templateVersionId: form.templateVersionId ?? null,
      testSuiteVersionId: form.testSuiteVersionId ?? null,
      runtimeId: form.runtimeId ?? null
    });
  };

  const serverFieldErrors = problemFieldErrors(createMutation.error);
  const errorFor = (field: string) => fieldErrors[field] ?? serverFieldErrors[field];

  const columnVisibilityModel = useNarrowColumns(
    ['assignmentKey', 'title', 'status', 'dueAt'],
    ['title', 'status']
  );

  const columns: GridColDef[] = useMemo(() => [
    {
      field: 'assignmentKey',
      headerName: 'Key',
      width: 230,
      // A bare router link renders in the browser's own blue and purple, which is neither
      // the theme's link colour nor readable against the dark surface.
      renderCell: (params: GridRenderCellParams<AssignmentDetail>) => (
        <Tooltip title={params.row.assignmentKey}>
          <Link component={RouterLink} to={`/assignments/${params.row.id}`} color="primary">
            {params.row.assignmentKey}
          </Link>
        </Tooltip>
      )
    },
    {
      field: 'title',
      headerName: 'Title',
      flex: 1,
      minWidth: 150,
      renderCell: (params: GridRenderCellParams<AssignmentDetail>) => (
        <Link component={RouterLink} to={`/assignments/${params.row.id}`} color="primary">
          {params.row.title}
        </Link>
      )
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 150,
      renderCell: (params: GridRenderCellParams<AssignmentDetail>) => <AssignmentStatusChip status={params.row.status} />
    },
    {
      field: 'dueAt',
      headerName: 'Due At',
      width: 200,
      valueGetter: (value: string | null | undefined) => (value ? new Date(value).toLocaleString() : '')
    }
  ], []);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
        <Typography variant="h4" component="h1">Assignments</Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', flex: '1 1 auto', justifyContent: 'flex-end' }}>
          <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 200 } }}>
            <InputLabel id="assignment-course-filter-label">Course Filter</InputLabel>
            <Select
              labelId="assignment-course-filter-label"
              value={selectedCourseId}
              label="Course Filter"
              onChange={(e) => { selectCourse(e.target.value); }}
            >
              <MenuItem value=""><em>All courses</em></MenuItem>
              {courses?.content.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
            </Select>
          </FormControl>
          <Button
            variant="contained"
            disabled={coursesFailed || !courses || courses.content.length === 0}
            onClick={() => {
              setForm({ ...emptyForm(), courseId: selectedCourseId || courses?.content[0]?.id || '' });
              setOpen(true);
            }}
          >
            New Assignment
          </Button>
        </Box>
      </Box>

      {/* Without the course list the filter is empty and the create dialog has nothing to
          attach an assignment to, so the page has to say so rather than present controls
          that cannot work. */}
      {coursesFailed && (
        <QueryErrorNotice
          message="The course list could not be loaded, so assignments cannot be filtered or created."
          onRetry={() => void refetchCourses()}
        />
      )}

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress aria-label="Loading assignments" />
        </Box>
      ) : isError ? (
        <QueryErrorNotice message="The assignments could not be loaded." onRetry={() => void refetch()} />
      ) : (
        <Box sx={{ height: 600, width: '100%', mt: 2 }}>
          <DataGrid
            rows={data?.content ?? []}
            columns={columns}
            columnVisibilityModel={columnVisibilityModel}
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

      <Dialog open={open} onClose={() => !createMutation.isPending && closeDialog()} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit}>
          <DialogTitle>New Assignment</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <MutationErrorAlert error={createMutation.error} />

            <FormControl fullWidth required error={!!errorFor('courseId')}>
              <InputLabel id="assignment-course-label">Course</InputLabel>
              <Select
                labelId="assignment-course-label"
                value={form.courseId ?? ''}
                label="Course"
                onChange={e => setForm({ ...form, courseId: e.target.value })}
                disabled={createMutation.isPending}
              >
                {courses?.content.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
              </Select>
              {errorFor('courseId') && <FormHelperText>{errorFor('courseId')}</FormHelperText>}
            </FormControl>

            <TextField label="Key" required fullWidth value={form.assignmentKey ?? ''} onChange={e => setForm({ ...form, assignmentKey: e.target.value })} error={!!errorFor('assignmentKey')} helperText={errorFor('assignmentKey')} disabled={createMutation.isPending} />
            <TextField label="Title" required fullWidth value={form.title ?? ''} onChange={e => setForm({ ...form, title: e.target.value })} error={!!errorFor('title')} helperText={errorFor('title')} disabled={createMutation.isPending} />
            <TextField select label="Status" required fullWidth value={form.status ?? 'DRAFT'} onChange={e => setForm({ ...form, status: e.target.value })} error={!!errorFor('status')} helperText={errorFor('status') ?? 'Anything other than DRAFT needs a template, a test suite, a runtime and both dates.'} disabled={createMutation.isPending}>
              {ASSIGNMENT_STATUSES.map(status => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </TextField>

            <TextField label="Timezone" fullWidth value={form.timezone ?? ''} onChange={e => setForm({ ...form, timezone: e.target.value })} error={!!errorFor('timezone')} helperText={errorFor('timezone') ?? 'The zone the two dates below are stated in.'} disabled={createMutation.isPending} />

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Opens At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.opensAt ?? ''} onChange={e => setForm({ ...form, opensAt: e.target.value })} error={!!errorFor('opensAt')} helperText={errorFor('opensAt')} disabled={createMutation.isPending} />
              <TextField label="Due At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.dueAt ?? ''} onChange={e => setForm({ ...form, dueAt: e.target.value })} error={!!errorFor('dueAt')} helperText={errorFor('dueAt')} disabled={createMutation.isPending} />
            </Box>

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Max Points" type="number" required fullWidth value={numberInputValue(form.maxPoints)} onChange={e => setForm({ ...form, maxPoints: parseNumberInput(e.target.value) })} error={!!errorFor('maxPoints')} helperText={errorFor('maxPoints')} disabled={createMutation.isPending} />
              <TextField label="Pass Threshold" type="number" required fullWidth value={numberInputValue(form.passThreshold)} onChange={e => setForm({ ...form, passThreshold: parseNumberInput(e.target.value) })} error={!!errorFor('passThreshold')} helperText={errorFor('passThreshold')} disabled={createMutation.isPending} />
              <TextField label="Test Count" type="number" required fullWidth value={numberInputValue(form.testCount)} onChange={e => setForm({ ...form, testCount: parseNumberInput(e.target.value) })} error={!!errorFor('testCount')} helperText={errorFor('testCount')} disabled={createMutation.isPending} />
            </Box>

            <Box sx={{ display: 'flex', gap: 2 }}>
              <FormControlLabel control={<Checkbox checked={form.mandatory ?? true} onChange={e => setForm({ ...form, mandatory: e.target.checked })} disabled={createMutation.isPending} />} label="Mandatory" />
              <FormControlLabel control={<Checkbox checked={form.allowLate ?? false} onChange={e => setForm({ ...form, allowLate: e.target.checked })} disabled={createMutation.isPending} />} label="Allow Late" />
            </Box>

            <Box sx={{ mt: 1 }}>
              <Typography variant="subtitle2" component="h3" gutterBottom>Sandbox Limits (Optional)</Typography>
              <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
                <TextField label="Timeout (s)" type="number" fullWidth value={numberInputValue(form.timeoutSeconds)} onChange={e => setForm({ ...form, timeoutSeconds: parseNumberInput(e.target.value) ?? null })} disabled={createMutation.isPending} />
                <TextField label="Memory (bytes)" type="number" fullWidth value={numberInputValue(form.memoryLimitBytes)} onChange={e => setForm({ ...form, memoryLimitBytes: parseNumberInput(e.target.value) ?? null })} disabled={createMutation.isPending} />
              </Box>
              <Box sx={{ display: 'flex', gap: 2, mb: 1 }}>
                <TextField label="CPU Limit" type="number" slotProps={{ htmlInput: { step: 0.1 } }} fullWidth value={numberInputValue(form.cpuLimit)} onChange={e => setForm({ ...form, cpuLimit: parseNumberInput(e.target.value) ?? null })} disabled={createMutation.isPending} />
                <TextField label="PID Limit" type="number" fullWidth value={numberInputValue(form.pidLimit)} onChange={e => setForm({ ...form, pidLimit: parseNumberInput(e.target.value) ?? null })} disabled={createMutation.isPending} />
              </Box>
              <FormControl error={!!errorFor('networkEnabled')}>
                <FormControlLabel control={<Switch checked={form.networkEnabled ?? false} onChange={e => setForm({ ...form, networkEnabled: e.target.checked })} disabled={createMutation.isPending} />} label="Network Enabled" />
                <FormHelperText>Warning: Enabling network access weakens sandbox isolation.</FormHelperText>
              </FormControl>
            </Box>

            <Typography variant="subtitle2" component="h3" gutterBottom sx={{ mt: 2 }}>Materials &amp; Environment (Optional)</Typography>
            <FormControl fullWidth>
              <InputLabel id="new-template-version-label">Template Version</InputLabel>
              <Select
                labelId="new-template-version-label"
                value={form.templateVersionId ?? ''}
                label="Template Version"
                onChange={e => setForm({ ...form, templateVersionId: e.target.value })}
                disabled={createMutation.isPending || materials.isLoading}
              >
                <MenuItem value=""><em>None</em></MenuItem>
                {materials.publishedTemplateVersions.map(tv => (
                  <MenuItem key={tv.id} value={tv.id}>{tv.label}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel id="new-test-suite-version-label">Test Suite Version</InputLabel>
              <Select
                labelId="new-test-suite-version-label"
                value={form.testSuiteVersionId ?? ''}
                label="Test Suite Version"
                onChange={e => setForm({ ...form, testSuiteVersionId: e.target.value })}
                disabled={createMutation.isPending || materials.isLoading}
              >
                <MenuItem value=""><em>None</em></MenuItem>
                {materials.publishedSuiteVersions.map(sv => (
                  <MenuItem key={sv.id} value={sv.id}>{sv.label}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel id="new-runtime-label">Runtime</InputLabel>
              <Select
                labelId="new-runtime-label"
                value={form.runtimeId ?? ''}
                label="Runtime"
                onChange={e => setForm({ ...form, runtimeId: e.target.value })}
                disabled={createMutation.isPending || materials.isLoading}
              >
                <MenuItem value=""><em>None</em></MenuItem>
                {materials.runtimes.map(rt => (
                  <MenuItem key={rt.id} value={rt.id}>{rt.displayName}</MenuItem>
                ))}
              </Select>
            </FormControl>

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
