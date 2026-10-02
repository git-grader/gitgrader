// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, AssignmentDefinitionSchema } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { AssignmentStatusChip } from '../components/AssignmentStatusChip';
import { MutationErrorAlert } from '../components/MutationErrorAlert';
import { SearchableChoice } from '../components/SearchableChoice';
import { AssignmentReadiness } from '../components/AssignmentReadiness';
import { DuplicateAssignment } from '../components/DuplicateAssignment';
import { PageHeader } from '../components/PageHeader';
import { numberInputValue, parseNumberInput } from '../components/numberInput';
import { fromZonedInputValue, toZonedInputValue } from '../components/localDateTime';
import type { AssignmentDefinition, AssignmentDetail } from '../api';
import { Typography, CircularProgress, Button, Paper, Alert, Tooltip, Box, TextField, Table, TableHead, TableRow, TableCell, TableBody, TableContainer } from '@mui/material';
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
  const [form, setForm] = useState<{ [K in keyof AssignmentDefinition]?: AssignmentDefinition[K] | undefined }>(() => ({
    ...assignment,
    opensAt: toZonedInputValue(assignment.opensAt, assignment.timezone),
    dueAt: toZonedInputValue(assignment.dueAt, assignment.timezone)
  }));

  const disabled = !isDraft || pending;
  const [validationError, setValidationError] = useState('');

  const handleSave = (event: React.SyntheticEvent) => {
    event.preventDefault();
    if (form.maxPoints === undefined || form.maxPoints < 0 || form.testCount === undefined || !Number.isInteger(form.testCount) || form.testCount < 0 || form.passThreshold === undefined || form.passThreshold < 0 || form.passThreshold > 100) {
      setValidationError('Enter non-negative points and test count, and a pass threshold from 0 to 100.'); return;
    }
    setValidationError('');
    // The two dates mean wall-clock time in the assignment's own zone, not the reader's.
    const zone = form.timezone ?? assignment.timezone;
    onSave(AssignmentDefinitionSchema.parse({
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
      networkEnabled: form.networkEnabled ?? assignment.networkEnabled,
      maxPoints: form.maxPoints, testCount: form.testCount, passThreshold: form.passThreshold,
      allowLate: form.allowLate ?? assignment.allowLate
    }));
  };

  return (
    <Paper id="assignment-configuration" tabIndex={-1} component="form" onSubmit={handleSave} sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 3, scrollMarginTop: 96 }}>
      <Typography variant="h6">Configuration</Typography>
      {validationError && <Alert severity="error">{validationError}</Alert>}
      <Typography variant="body2" color="text.secondary">Dates use {assignment.timezone || 'your local timezone'}.</Typography>

      <SearchableChoice label="Template Version" value={form.templateVersionId ?? ''} options={materials.publishedTemplateVersions}
        onChange={(value) => setForm({ ...form, templateVersionId: value })} disabled={disabled} loading={materials.isLoading} />
      <SearchableChoice label="Test Suite Version" value={form.testSuiteVersionId ?? ''} options={materials.publishedSuiteVersions}
        onChange={(value) => setForm({ ...form, testSuiteVersionId: value })} disabled={disabled} loading={materials.isLoading} />
      <SearchableChoice label="Runtime" value={form.runtimeId ?? ''} options={materials.runtimes.map((runtime) => ({ id: runtime.id, label: runtime.displayName }))}
        onChange={(value) => setForm({ ...form, runtimeId: value })} disabled={disabled} loading={materials.isLoading} />

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
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

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, minmax(0, 1fr))' }, gap: 2 }}>
        <TextField label="Max Points" type="number" required value={numberInputValue(form.maxPoints)} disabled={disabled} onChange={event => setForm({ ...form, maxPoints: parseNumberInput(event.target.value) })} />
        <TextField label="Test Count" type="number" required value={numberInputValue(form.testCount)} disabled={disabled} onChange={event => setForm({ ...form, testCount: parseNumberInput(event.target.value) })} />
        <TextField label="Pass Threshold" type="number" required value={numberInputValue(form.passThreshold)} disabled={disabled} onChange={event => setForm({ ...form, passThreshold: parseNumberInput(event.target.value) })} />
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

  const extensionsQuery = useQuery({
    queryKey: queryKeys.assignments.extensions(id ?? ''),
    queryFn: () => api.getAssignmentExtensions(id ?? ''),
    enabled: !!id
  });
  const [extensionForm, setExtensionForm] = useState({ studentId: '', extendedDueAt: '', reason: '' });
  const grantExtensionMutation = useMutation({
    mutationFn: () => api.grantAssignmentExtension(id ?? '', {
      studentId: extensionForm.studentId,
      extendedDueAt: new Date(extensionForm.extendedDueAt).toISOString(),
      reason: extensionForm.reason
    }),
    onSuccess: () => {
      setExtensionForm({ studentId: '', extendedDueAt: '', reason: '' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.assignments.extensions(id ?? '') });
    }
  });
  const revokeExtensionMutation = useMutation({
    mutationFn: (extensionId: string) => api.revokeAssignmentExtension(id ?? '', extensionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.assignments.extensions(id ?? '') });
    }
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
      <PageHeader title={assignment.title} description={`Key: ${assignment.assignmentKey}`} actions={
        <>
          <AssignmentStatusChip status={assignment.status} />
          <DuplicateAssignment assignment={assignment} />
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
        </>
      } />

      <MutationErrorAlert error={error} />
      {!isDraft && <Alert severity="info">Published assignments are immutable. Configuration cannot be changed.</Alert>}
      {updateMutation.isSuccess && <Alert severity="success">Assignment updated successfully.</Alert>}

      <AssignmentReadiness assignment={assignment} materials={materials} />

      <ConfigurationForm
        key={assignment.id}
        assignment={assignment}
        materials={materials}
        isDraft={isDraft}
        pending={updateMutation.isPending}
        onSave={(request) => updateMutation.mutate(request)}
      />

      <Paper sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Typography variant="h6">Deadline Extensions</Typography>
        <Typography variant="body2" color="text.secondary">
          Give an individual student more time without changing the deadline for the class.
        </Typography>
        {extensionsQuery.isLoading ? (
          <CircularProgress aria-label="Loading extensions" />
        ) : extensionsQuery.isError ? (
          <QueryErrorNotice message="The extensions could not be loaded." onRetry={() => void extensionsQuery.refetch()} />
        ) : (extensionsQuery.data ?? []).length === 0 ? (
          <Typography variant="body2" color="text.secondary">No extensions granted.</Typography>
        ) : (
          <TableContainer sx={{ width: '100%', overflowX: 'auto' }}>
          <Table size="small" sx={{ minWidth: 650 }}>
            <TableHead>
              <TableRow>
                <TableCell>Student</TableCell>
                <TableCell>Extended due</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell>Revoked</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(extensionsQuery.data ?? []).map((ext) => (
                <TableRow key={ext.id}>
                  <TableCell sx={{ overflowWrap: 'anywhere' }}>{ext.studentId}</TableCell>
                  <TableCell>{new Date(ext.extendedDueAt).toLocaleString()}</TableCell>
                  <TableCell sx={{ overflowWrap: 'anywhere' }}>{ext.reason}</TableCell>
                  <TableCell>{ext.revokedAt ? new Date(ext.revokedAt).toLocaleString() : '—'}</TableCell>
                  <TableCell align="right">
                    {!ext.revokedAt && (
                      <Button
                        size="small"
                        color="warning"
                        disabled={revokeExtensionMutation.isPending}
                        onClick={() => { if (window.confirm('Revoke this extension?')) revokeExtensionMutation.mutate(ext.id); }}
                      >
                        Revoke
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          </TableContainer>
        )}
        <MutationErrorAlert error={grantExtensionMutation.error ?? revokeExtensionMutation.error} />
        <Box component="form" sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 2 }} onSubmit={(e) => { e.preventDefault(); grantExtensionMutation.mutate(); }}>
          <TextField
            label="Student ID"
            required
            value={extensionForm.studentId}
            onChange={(e) => setExtensionForm({ ...extensionForm, studentId: e.target.value })}
            disabled={grantExtensionMutation.isPending}
            sx={{ minWidth: 0 }}
          />
          <TextField
            label="Extended Due At"
            type="datetime-local"
            required
            slotProps={{ inputLabel: { shrink: true } }}
            value={extensionForm.extendedDueAt}
            onChange={(e) => setExtensionForm({ ...extensionForm, extendedDueAt: e.target.value })}
            disabled={grantExtensionMutation.isPending}
            sx={{ minWidth: 0 }}
          />
          <TextField
            label="Reason"
            required
            value={extensionForm.reason}
            onChange={(e) => setExtensionForm({ ...extensionForm, reason: e.target.value })}
            disabled={grantExtensionMutation.isPending}
            sx={{ minWidth: 0 }}
          />
          <Button type="submit" variant="outlined" disabled={grantExtensionMutation.isPending}>
            {grantExtensionMutation.isPending ? 'Granting…' : 'Grant extension'}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
}
