// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { AssignmentStatusChip } from '../components/AssignmentStatusChip';
import { MutationErrorAlert } from '../components/MutationErrorAlert';
import { fromZonedInputValue, toZonedInputValue } from '../components/localDateTime';
import type { AssignmentDefinition, AssignmentDetail } from '../api';
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
    opensAt: toZonedInputValue(assignment.opensAt, assignment.timezone),
    dueAt: toZonedInputValue(assignment.dueAt, assignment.timezone)
  }));

  const disabled = !isDraft || pending;

  // The control is narrow on a phone and disabled once published, so its label is
  // truncated with no way to open the list and read the rest. The tooltip carries it.
  const selectedSuiteLabel =
    materials.publishedSuiteVersions.find((version) => version.id === form.testSuiteVersionId)?.label ?? '';

  const handleSave = (event: React.SyntheticEvent) => {
    event.preventDefault();
    // The two dates mean wall-clock time in the assignment's own zone, not the reader's.
    const zone = form.timezone ?? assignment.timezone;
    onSave({
      ...assignment,
      ...form,
      courseId: assignment.courseId,
      assignmentKey: form.assignmentKey || assignment.assignmentKey,
      title: form.title || assignment.title,
      status: assignment.status,
      displayOrder: form.displayOrder ?? assignment.displayOrder,
      mandatory: form.mandatory ?? assignment.mandatory,
      opensAt: fromZonedInputValue(form.opensAt, zone),
      dueAt: fromZonedInputValue(form.dueAt, zone),
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
        <Tooltip title={selectedSuiteLabel}>
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
        </Tooltip>
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

  const { data: assignment, isLoading: assignmentLoading, isError: assignmentFailed, refetch: refetchAssignment } = useQuery({
    queryKey: queryKeys.assignments.detail(id ?? ''),
    queryFn: () => api.getAssignment(id ?? ''),
    enabled: !!id
  });

  const materials = useAssignmentMaterials();

  // Both endpoints answer with the updated assignment, so the cache is corrected from
  // the response rather than left stale until a refetch lands.
  const applyUpdated = (updated: AssignmentDetail) => {
    queryClient.setQueryData(queryKeys.assignments.detail(updated.id), updated);
    void queryClient.invalidateQueries({ queryKey: queryKeys.assignments.all });
  };

  const updateMutation = useMutation({
    mutationFn: (request: AssignmentDefinition) => api.updateAssignment(id ?? '', request),
    onSuccess: applyUpdated
  });

  const publishMutation = useMutation({
    mutationFn: () => api.publishAssignment(id ?? ''),
    onSuccess: applyUpdated
  });

  if (assignmentLoading || materials.isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress aria-label="Loading assignment" />
      </Box>
    );
  }
  if (materials.isError) {
    return (
      <Alert severity="error" sx={{ m: 2 }}>
        The templates, test suites and runtimes could not be loaded, so this assignment cannot be
        configured. Reload the page to try again.
      </Alert>
    );
  }
  // A failed request is not a missing assignment, and saying so sends an instructor
  // looking for something they deleted rather than reloading the page.
  if (assignmentFailed) {
    return <QueryErrorNotice message="The assignment could not be loaded." onRetry={() => void refetchAssignment()} />;
  }
  if (!assignment) {
    return <QueryErrorNotice message="The assignment could not be loaded." onRetry={() => void refetchAssignment()} />;
  }

  const error: unknown = publishMutation.error ?? updateMutation.error;

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
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              Key: {assignment.assignmentKey}
            </Typography>
            <AssignmentStatusChip status={assignment.status} />
          </Box>
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

      <MutationErrorAlert error={error} />
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
