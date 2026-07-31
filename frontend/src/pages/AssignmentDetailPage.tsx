// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import type { AssignmentDefinition, AssignmentDetail } from '../api';
import { ApiProblem } from '../api/client';
import { Typography, CircularProgress, Button, Paper, Alert, Tooltip, Box, FormControl, InputLabel, Select, MenuItem, TextField } from '@mui/material';
import { useAssignmentMaterials } from '../hooks/useAssignmentMaterials';

type Materials = ReturnType<typeof useAssignmentMaterials>;

interface ConfigurationFormProps {
  assignment: AssignmentDetail;
  materials: Materials;
  isDraft: boolean;
  pending: boolean;
  onSave: (request: AssignmentDefinition) => void;
}

function ConfigurationForm({ assignment, materials, isDraft, pending, onSave }: ConfigurationFormProps) {
  // Seeded from the assignment rather than synchronised in an effect; the parent
  // remounts this form via a key when a different assignment is opened.
  const [form, setForm] = useState<Partial<AssignmentDefinition>>(() => ({
    ...assignment,
    opensAt: assignment.opensAt ? assignment.opensAt.slice(0, 16) : '',
    dueAt: assignment.dueAt ? assignment.dueAt.slice(0, 16) : ''
  }));

  const disabled = !isDraft || pending;

  const handleSave = (event: React.SyntheticEvent) => {
    event.preventDefault();
    const toIso = (value?: string | null) => (value ? new Date(value).toISOString() : null);
    onSave({
      ...assignment,
      ...form,
      courseId: assignment.courseId,
      assignmentKey: form.assignmentKey || assignment.assignmentKey,
      title: form.title || assignment.title,
      status: assignment.status,
      displayOrder: form.displayOrder ?? assignment.displayOrder,
      mandatory: form.mandatory ?? assignment.mandatory,
      opensAt: toIso(form.opensAt),
      dueAt: toIso(form.dueAt),
      templateVersionId: form.templateVersionId || null,
      testSuiteVersionId: form.testSuiteVersionId || null,
      runtimeId: form.runtimeId || null,
      networkEnabled: form.networkEnabled ?? assignment.networkEnabled
    });
  };

  return (
    <Paper component="form" onSubmit={handleSave} sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Typography variant="h6">Configuration</Typography>

      <FormControl fullWidth>
        <InputLabel id="template-version-label">Template Version</InputLabel>
        <Select
          labelId="template-version-label"
          value={form.templateVersionId || ''}
          label="Template Version"
          onChange={(event) => setForm({ ...form, templateVersionId: event.target.value })}
          disabled={disabled}
        >
          <MenuItem value=""><em>None</em></MenuItem>
          {materials.publishedTemplateVersions.map((version) => (
            <MenuItem key={version.id} value={version.id}>{version.label}</MenuItem>
          ))}
        </Select>
      </FormControl>

      <FormControl fullWidth>
        <InputLabel id="test-suite-version-label">Test Suite Version</InputLabel>
        <Select
          labelId="test-suite-version-label"
          value={form.testSuiteVersionId || ''}
          label="Test Suite Version"
          onChange={(event) => setForm({ ...form, testSuiteVersionId: event.target.value })}
          disabled={disabled}
        >
          <MenuItem value=""><em>None</em></MenuItem>
          {materials.publishedSuiteVersions.map((version) => (
            <MenuItem key={version.id} value={version.id}>{version.label}</MenuItem>
          ))}
        </Select>
      </FormControl>

      <FormControl fullWidth>
        <InputLabel id="runtime-label">Runtime</InputLabel>
        <Select
          labelId="runtime-label"
          value={form.runtimeId || ''}
          label="Runtime"
          onChange={(event) => setForm({ ...form, runtimeId: event.target.value })}
          disabled={disabled}
        >
          <MenuItem value=""><em>None</em></MenuItem>
          {materials.runtimes.map((runtime) => (
            <MenuItem key={runtime.id} value={runtime.id}>{runtime.displayName}</MenuItem>
          ))}
        </Select>
      </FormControl>

      <Box sx={{ display: 'flex', gap: 2 }}>
        <TextField
          label="Opens At"
          type="datetime-local"
          fullWidth
          slotProps={{ inputLabel: { shrink: true } }}
          value={form.opensAt || ''}
          onChange={(event) => setForm({ ...form, opensAt: event.target.value })}
          disabled={disabled}
        />
        <TextField
          label="Due At"
          type="datetime-local"
          fullWidth
          slotProps={{ inputLabel: { shrink: true } }}
          value={form.dueAt || ''}
          onChange={(event) => setForm({ ...form, dueAt: event.target.value })}
          disabled={disabled}
        />
      </Box>

      <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
        <Button type="submit" variant="contained" disabled={disabled}>Save Configuration</Button>
      </Box>
    </Paper>
  );
}

export function AssignmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();

  const { data: assignment, isLoading: assignmentLoading } = useQuery({
    queryKey: ['assignments', 'detail', id],
    queryFn: () => api.getAssignment(id || '')
  });

  const materials = useAssignmentMaterials();

  const updateMutation = useMutation({
    mutationFn: (request: AssignmentDefinition) => api.updateAssignment(id || '', request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['assignments', 'detail', id] });
    }
  });

  const publishMutation = useMutation({
    mutationFn: () => api.publishAssignment(id || ''),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['assignments', 'detail', id] });
    }
  });

  if (assignmentLoading || materials.isLoading) {
    return <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress /></Box>;
  }
  if (!assignment) return <Typography color="error">Assignment not found</Typography>;

  const error = (publishMutation.error || updateMutation.error) as ApiProblem | null;

  const missing: string[] = [];
  if (!assignment.templateVersionId) missing.push('a template version');
  if (!assignment.testSuiteVersionId) missing.push('a test suite version');
  if (!assignment.runtimeId) missing.push('a runtime');
  if (!(assignment.opensAt && assignment.dueAt && new Date(assignment.opensAt) < new Date(assignment.dueAt))) {
    missing.push('a due date after the opening date');
  }

  const isDraft = assignment.status === 'DRAFT';
  const publishTooltip = missing.length ? `Needs: ${missing.join(', ')}.` : '';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Box>
          <Typography variant="h4" component="h1">{assignment.title}</Typography>
          <Typography color="text.secondary">Key: {assignment.assignmentKey} | Status: {assignment.status}</Typography>
        </Box>
        <Tooltip title={publishTooltip}>
          <span>
            <Button
              variant="contained"
              color="primary"
              onClick={() => publishMutation.mutate()}
              disabled={publishMutation.isPending || missing.length > 0 || !isDraft}
            >
              {isDraft ? 'Publish' : 'Published'}
            </Button>
          </span>
        </Tooltip>
      </Box>

      {error && <Alert severity="error">{error.detail || error.title}</Alert>}
      {!isDraft && <Alert severity="info">Published assignments are immutable. Configuration cannot be changed.</Alert>}
      {updateMutation.isSuccess && <Alert severity="success">Assignment updated successfully.</Alert>}

      <ConfigurationForm
        key={assignment.id}
        assignment={assignment}
        materials={materials}
        isDraft={isDraft}
        pending={updateMutation.isPending}
        onSave={(request) => updateMutation.mutate(request)}
      />
    </Box>
  );
}
