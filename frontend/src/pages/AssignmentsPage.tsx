// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAssignmentMaterials } from '../hooks/useAssignmentMaterials';
import { api } from '../api';
import type { AssignmentDefinition } from '../api';
import { ApiProblem } from '../api/client';
import { Typography, CircularProgress, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Alert, Select, MenuItem, InputLabel, FormControl, FormControlLabel, Checkbox, Switch, FormHelperText, Tooltip } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import { AssignmentStatusChip } from '../components/AssignmentStatusChip';
import { useNarrowColumns } from '../components/responsiveColumns';
import { useServerPagination, CHOICE_PAGE_SIZE } from '../components/useServerPagination';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';

export function AssignmentsPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedCourseId = searchParams.get('courseId') || '';

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Partial<AssignmentDefinition>>({
    assignmentKey: '', title: '', description: '', displayOrder: 10, status: 'DRAFT', mandatory: true,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone, maxPoints: 10, testCount: 0, passThreshold: 0, allowLate: false,
    networkEnabled: false, timeoutSeconds: null, memoryLimitBytes: null, cpuLimit: null, pidLimit: null, templateVersionId: null, testSuiteVersionId: null, runtimeId: null
  });

  const { data: courses } = useQuery({
    queryKey: ['courses', 'choices'],
    queryFn: () => api.getCourses({ size: CHOICE_PAGE_SIZE })
  });
  const materials = useAssignmentMaterials();
  
  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading } = useQuery({
    queryKey: ['assignments', selectedCourseId, params.page, params.size],
    queryFn: () => api.getAssignments(selectedCourseId ? { ...params, courseId: selectedCourseId } : params),
    placeholderData: (previous) => previous
  });

  const createMutation = useMutation({
    mutationFn: (req: AssignmentDefinition) => api.createAssignment(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['assignments'] });
      setOpen(false);
      setForm({
        assignmentKey: '', title: '', description: '', displayOrder: 10, status: 'DRAFT', mandatory: true,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone, maxPoints: 10, testCount: 0, passThreshold: 0, allowLate: false,
        networkEnabled: false, timeoutSeconds: null, memoryLimitBytes: null, cpuLimit: null, pidLimit: null
      });
    }
  });

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    if (!form.courseId) return;

    const toIso = (val?: string | null) => val ? new Date(val).toISOString() : null;

    const toEmptyNull = (val?: string | null) => (val && val.trim() !== '') ? val : null;
    const toNumberNull = (val?: number | string | null) => (val !== undefined && val !== null && val !== '') ? Number(val) : null;

    const payload: AssignmentDefinition = {
      ...form,
      courseId: form.courseId,
      assignmentKey: form.assignmentKey || '',
      title: form.title || '',
      description: toEmptyNull(form.description),
      status: form.status || 'DRAFT',
      timezone: toEmptyNull(form.timezone),
      displayOrder: form.displayOrder || 10,
      mandatory: form.mandatory ?? true,
      maxPoints: form.maxPoints || 0,
      testCount: form.testCount || 0,
      passThreshold: form.passThreshold || 0,
      allowLate: form.allowLate ?? false,
      opensAt: toIso(form.opensAt),
      dueAt: toIso(form.dueAt),
      networkEnabled: form.networkEnabled ?? false,
      timeoutSeconds: toNumberNull(form.timeoutSeconds),
      memoryLimitBytes: toNumberNull(form.memoryLimitBytes),
      cpuLimit: toNumberNull(form.cpuLimit),
      pidLimit: toNumberNull(form.pidLimit)
    };

    createMutation.mutate(payload);
  };

  const err = createMutation.error as ApiProblem | null;
  const fieldErrors = err?.errors?.reduce((acc, curr) => ({ ...acc, [curr.field]: curr.message }), {} as Record<string, string>) || {};

  const columnVisibilityModel = useNarrowColumns(
    ['assignmentKey', 'title', 'status', 'dueAt'],
    ['title', 'status']
  );

  const columns: GridColDef[] = useMemo(() => [
    { 
      field: 'assignmentKey',
      headerName: 'Key',
      width: 230,
      renderCell: (params: GridRenderCellParams) => (
        <Tooltip title={String(params.value)}>
          <Link to={`/assignments/${params.row.id}`}>{String(params.value)}</Link>
        </Tooltip>
      )
    },
    {
      field: 'title',
      headerName: 'Title',
      flex: 1,
      minWidth: 150,
      renderCell: (params: GridRenderCellParams) => (
        <Link to={`/assignments/${params.row.id}`}>{String(params.value)}</Link>
      )
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 150,
      renderCell: (params: GridRenderCellParams) => <AssignmentStatusChip status={String(params.value)} />
    },
    {
      field: 'dueAt',
      headerName: 'Due At',
      width: 200,
      valueGetter: (value: string) => (value ? new Date(value).toLocaleString() : '')
    }
  ], []);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
        <Typography variant="h4" component="h1">Assignments</Typography>
        <div style={{ display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap', flex: '1 1 auto', justifyContent: 'flex-end' }}>
          <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 200 } }}>
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
          <Button variant="contained" onClick={() => {
            setForm(prev => ({ ...prev, courseId: selectedCourseId || (courses?.content[0]?.id ?? '') }));
            setOpen(true);
          }}>
            New Assignment
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '32px' }}><CircularProgress /></div>
      ) : (
        <div style={{ height: 600, width: '100%', marginTop: '16px' }}>
          <DataGrid
            rows={data?.content || []}
            columns={columns}
            columnVisibilityModel={columnVisibilityModel}
            paginationMode="server"
            rowCount={data?.totalElements ?? 0}
            paginationModel={paginationModel}
            onPaginationModelChange={setPaginationModel}
            pageSizeOptions={[20, 50, 100]}
            disableRowSelectionOnClick
          />
        </div>
      )}

      <Dialog open={open} onClose={() => !createMutation.isPending && setOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit}>
          <DialogTitle>New Assignment</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {err && <Alert severity="error">{err.detail || err.title}</Alert>}
            
            <FormControl fullWidth required error={!!fieldErrors['courseId']}>
              <InputLabel>Course</InputLabel>
              <Select
                value={form.courseId || ''}
                label="Course"
                onChange={e => setForm({ ...form, courseId: e.target.value })}
                disabled={createMutation.isPending}
              >
                {courses?.content.map(c => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
              </Select>
            </FormControl>

            <TextField label="Key" required fullWidth value={form.assignmentKey} onChange={e => setForm({ ...form, assignmentKey: e.target.value })} error={!!fieldErrors['assignmentKey']} helperText={fieldErrors['assignmentKey']} disabled={createMutation.isPending} />
            <TextField label="Title" required fullWidth value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} error={!!fieldErrors['title']} helperText={fieldErrors['title']} disabled={createMutation.isPending} />
            <TextField select label="Status" required fullWidth value={form.status} onChange={e => setForm({ ...form, status: e.target.value })} error={!!fieldErrors['status']} helperText={fieldErrors['status'] || 'Anything other than DRAFT needs a template, a test suite, a runtime and both dates.'} disabled={createMutation.isPending}>
              <MenuItem value="DRAFT">DRAFT</MenuItem>
              <MenuItem value="SCHEDULED">SCHEDULED</MenuItem>
              <MenuItem value="OPEN">OPEN</MenuItem>
              <MenuItem value="CLOSED">CLOSED</MenuItem>
              <MenuItem value="ARCHIVED">ARCHIVED</MenuItem>
            </TextField>
            
            <div style={{ display: 'flex', gap: '16px' }}>
              <TextField label="Opens At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.opensAt || ''} onChange={e => setForm({ ...form, opensAt: e.target.value })} error={!!fieldErrors['opensAt']} helperText={fieldErrors['opensAt']} disabled={createMutation.isPending} />
              <TextField label="Due At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.dueAt || ''} onChange={e => setForm({ ...form, dueAt: e.target.value })} error={!!fieldErrors['dueAt']} helperText={fieldErrors['dueAt']} disabled={createMutation.isPending} />
            </div>

            <div style={{ display: 'flex', gap: '16px' }}>
              <TextField label="Max Points" type="number" required fullWidth value={form.maxPoints} onChange={e => setForm({ ...form, maxPoints: parseFloat(e.target.value) })} error={!!fieldErrors['maxPoints']} helperText={fieldErrors['maxPoints']} disabled={createMutation.isPending} />
              <TextField label="Pass Threshold" type="number" required fullWidth value={form.passThreshold} onChange={e => setForm({ ...form, passThreshold: parseFloat(e.target.value) })} error={!!fieldErrors['passThreshold']} helperText={fieldErrors['passThreshold']} disabled={createMutation.isPending} />
              <TextField label="Test Count" type="number" required fullWidth value={form.testCount} onChange={e => setForm({ ...form, testCount: parseInt(e.target.value) })} error={!!fieldErrors['testCount']} helperText={fieldErrors['testCount']} disabled={createMutation.isPending} />
            </div>

            <div style={{ display: 'flex', gap: '16px' }}>
              <FormControlLabel control={<Checkbox checked={form.mandatory} onChange={e => setForm({ ...form, mandatory: e.target.checked })} disabled={createMutation.isPending} />} label="Mandatory" />
              <FormControlLabel control={<Checkbox checked={form.allowLate} onChange={e => setForm({ ...form, allowLate: e.target.checked })} disabled={createMutation.isPending} />} label="Allow Late" />
            </div>
            
            <div style={{ marginTop: '8px' }}>
              <Typography variant="subtitle2" gutterBottom>Sandbox Limits (Optional)</Typography>
              <div style={{ display: 'flex', gap: '16px', marginBottom: '16px' }}>
                <TextField label="Timeout (s)" type="number" fullWidth value={form.timeoutSeconds || ''} onChange={e => setForm({ ...form, timeoutSeconds: e.target.value ? parseInt(e.target.value) : null })} disabled={createMutation.isPending} />
                <TextField label="Memory (bytes)" type="number" fullWidth value={form.memoryLimitBytes || ''} onChange={e => setForm({ ...form, memoryLimitBytes: e.target.value ? parseInt(e.target.value) : null })} disabled={createMutation.isPending} />
              </div>
              <div style={{ display: 'flex', gap: '16px', marginBottom: '8px' }}>
                <TextField label="CPU Limit" type="number" slotProps={{ htmlInput: { step: 0.1 } }} fullWidth value={form.cpuLimit || ''} onChange={e => setForm({ ...form, cpuLimit: e.target.value ? parseFloat(e.target.value) : null })} disabled={createMutation.isPending} />
                <TextField label="PID Limit" type="number" fullWidth value={form.pidLimit || ''} onChange={e => setForm({ ...form, pidLimit: e.target.value ? parseInt(e.target.value) : null })} disabled={createMutation.isPending} />
              </div>
              <FormControl error={!!fieldErrors['networkEnabled']}>
                <FormControlLabel control={<Switch checked={form.networkEnabled} onChange={e => setForm({ ...form, networkEnabled: e.target.checked })} disabled={createMutation.isPending} />} label="Network Enabled" />
                <FormHelperText>Warning: Enabling network access weakens sandbox isolation.</FormHelperText>
              </FormControl>
            </div>
          
            <Typography variant="subtitle2" gutterBottom sx={{ mt: 2 }}>Materials & Environment (Optional)</Typography>
            <FormControl fullWidth>
              <InputLabel>Template Version</InputLabel>
              <Select
                value={form.templateVersionId || ''}
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
              <InputLabel>Test Suite Version</InputLabel>
              <Select
                value={form.testSuiteVersionId || ''}
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
              <InputLabel>Runtime</InputLabel>
              <Select
                value={form.runtimeId || ''}
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
            <Button onClick={() => setOpen(false)} disabled={createMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating...' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </div>
  );
}
