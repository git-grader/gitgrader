// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  LEGACY_TOPOLOGY,
  REPORT_FORMATS,
  RuntimeDefinitionSchema,
  SHIM_KINDS,
  api
} from '../api';
import type { Runtime, RuntimeDefinition } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import { PageHeader } from '../components/PageHeader';
import { tablePageSx, tablePanelSx } from '../components/pageLayout';
import { useIsNarrow } from '../components/responsiveColumns';
import { InstructorDataGrid } from '../components/InstructorDataGrid';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import {
  Box, Typography, CircularProgress, Button, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, FormControl, InputLabel, Select, MenuItem,
  FormControlLabel, Checkbox, Alert, Chip, Tooltip, FormHelperText
} from '@mui/material';

const EMPTY_FORM: Partial<RuntimeDefinition> = { enabled: true, reportFormat: 'JUNIT_XML', shimKind: LEGACY_TOPOLOGY };

/** A digest belongs to one immutable image build; the shortened form is the readable end. */
const shortDigest = (digest: string) => `${digest.slice(0, 7)}…${digest.slice(-7)}`;

export function AdminRuntimesPage() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Partial<RuntimeDefinition>>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const { data: me, isLoading: meLoading, isError: meFailed, refetch: refetchMe } = useQuery({
    queryKey: queryKeys.me,
    queryFn: api.getMe
  });

  const { data: runtimes, isLoading: runtimesLoading, isError: runtimesFailed, refetch: refetchRuntimes } = useQuery({
    queryKey: queryKeys.runtimes,
    queryFn: api.getRuntimes
  });

  const createMutation = useMutation({
    mutationFn: (req: RuntimeDefinition) => api.createRuntime(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.runtimes });
      closeDialog();
    }
  });

  // Declared before the early returns below: a hook must run on every render.
  const isNarrow = useIsNarrow();

  function closeDialog() {
    setOpen(false);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    createMutation.reset();
  }

  if (runtimesLoading || meLoading) {
    return <Box sx={{ p: 4 }}><CircularProgress aria-label="Loading runtimes" /></Box>;
  }
  // Who is signed in decides whether the create control appears at all, so a failed
  // `me` call would quietly present a read-only page to an administrator.
  if (runtimesFailed || meFailed || !runtimes || !me) {
    return (
      <QueryErrorNotice
        message="The runtimes could not be loaded."
        onRetry={() => {
          void refetchRuntimes();
          void refetchMe();
        }}
      />
    );
  }

  const isAdmin = me.roles.includes('ROLE_ADMIN');
  const serverFieldErrors = problemFieldErrors(createMutation.error);
  const errorFor = (field: string) => fieldErrors[field] ?? serverFieldErrors[field];

  /**
   * Checks the form against the schema that describes the request.
   *
   * These rules were written out twice - once here and once in the schema - and only
   * this copy ever ran, so the digest pattern and the refusal of a `latest` tag could
   * drift apart without anything noticing.
   */
  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    const parsed = RuntimeDefinitionSchema.safeParse(form);
    if (!parsed.success) {
      const errors: Record<string, string> = {};
      for (const issue of parsed.error.issues) {
        const field = issue.path[0];
        if (typeof field === 'string' && !(field in errors)) {
          errors[field] = issue.message;
        }
      }
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    createMutation.mutate(parsed.data);
  };

  const wideColumns: GridColDef[] = [
    { field: 'displayName', headerName: 'Display Name', flex: 1, minWidth: 160 },
    { field: 'runtimeKey', headerName: 'Key', width: 130 },
    {
      field: 'image',
      headerName: 'Image',
      width: 210,
      valueGetter: (value, row: Runtime) => `${value}:${row.tag}`
    },
    {
      field: 'imageDigest',
      headerName: 'Image Digest',
      width: 170,
      renderCell: (params: GridRenderCellParams<Runtime>) => (
        <Tooltip title={params.row.imageDigest}>
          <Typography component="span" tabIndex={params.hasFocus ? 0 : -1} sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
            {shortDigest(params.row.imageDigest)}
          </Typography>
        </Tooltip>
      )
    },
    { field: 'reportFormat', headerName: 'Report Format', width: 150 },
    {
      field: 'enabled',
      headerName: 'Status',
      width: 120,
      renderCell: (params: GridRenderCellParams<Runtime>) => (
        <Chip
          size="small"
          variant={params.row.enabled ? 'filled' : 'outlined'}
          color={params.row.enabled ? 'success' : 'default'}
          label={params.row.enabled ? 'Enabled' : 'Disabled'}
        />
      )
    },
    { field: 'testCommand', headerName: 'Test Command', flex: 1, minWidth: 240 }
  ];

  /**
   * One stacked cell per runtime, used instead of columns on a narrow screen.
   *
   * Runtimes have no detail route, so every field the list shows is all there is: the
   * stacked cell keeps the digest, format, and command reachable instead of hiding them
   * with a desktop-column switcher.
   */
  const narrowColumn: GridColDef = {
    field: 'displayName',
    headerName: 'Runtime',
    flex: 1,
    minWidth: 240,
    renderCell: (params: GridRenderCellParams<Runtime>) => {
      const row = params.row;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="body2">{row.displayName}</Typography>
            <Chip
              size="small"
              variant={row.enabled ? 'filled' : 'outlined'}
              color={row.enabled ? 'success' : 'default'}
              label={row.enabled ? 'Enabled' : 'Disabled'}
            />
          </Box>
          <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {row.runtimeKey} · {row.image}:{row.tag}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere' }}>
            {row.imageDigest}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {row.reportFormat} · {row.testCommand}
          </Typography>
        </Box>
      );
    }
  };

  return (
    <Box sx={tablePageSx}>
      <PageHeader title="Runtimes" actions={isAdmin ? (
          <Button variant="contained" onClick={() => setOpen(true)}>New Runtime</Button>
        ) : (
          <Typography color="text.secondary">An administrator must add runtimes.</Typography>
        )} />

      {runtimes.length === 0 ? (
        <Alert severity="info">No runtimes configured. At least one runtime is required to publish assignments.</Alert>
      ) : (
        <Box component="section" aria-label="Runtime results" sx={tablePanelSx}>
          <InstructorDataGrid
            rows={runtimes}
            columns={isNarrow ? [narrowColumn] : wideColumns}
            {...(isNarrow ? { getRowHeight: () => 'auto' as const } : {})}
            disableRowSelectionOnClick
            sx={{ minWidth: 0 }}
          />
        </Box>
      )}

      <Dialog open={open} onClose={() => !createMutation.isPending && closeDialog()} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit}>
          <DialogTitle>New Runtime</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 2 }}>
            <MutationErrorAlert error={createMutation.error} />

            <TextField label="Key" required fullWidth value={form.runtimeKey ?? ''} onChange={e => setForm({ ...form, runtimeKey: e.target.value })} error={!!errorFor('runtimeKey')} helperText={errorFor('runtimeKey')} disabled={createMutation.isPending} />
            <TextField label="Display Name" required fullWidth value={form.displayName ?? ''} onChange={e => setForm({ ...form, displayName: e.target.value })} error={!!errorFor('displayName')} helperText={errorFor('displayName')} disabled={createMutation.isPending} />
            <TextField label="Image" required fullWidth value={form.image ?? ''} onChange={e => setForm({ ...form, image: e.target.value })} error={!!errorFor('image')} helperText={errorFor('image')} disabled={createMutation.isPending} />
            <TextField label="Tag" required fullWidth value={form.tag ?? ''} onChange={e => setForm({ ...form, tag: e.target.value })} error={!!errorFor('tag')} helperText={errorFor('tag')} disabled={createMutation.isPending} />
            <TextField label="Image Digest" required fullWidth value={form.imageDigest ?? ''} onChange={e => setForm({ ...form, imageDigest: e.target.value })} error={!!errorFor('imageDigest')} helperText={errorFor('imageDigest') ?? 'Pins the image so a rebuild cannot change what students are graded in.'} disabled={createMutation.isPending} />
            <TextField label="Install Command (Optional)" fullWidth value={form.installCommand ?? ''} onChange={e => setForm({ ...form, installCommand: e.target.value || null })} disabled={createMutation.isPending} />
            <TextField label="Test Command" required fullWidth value={form.testCommand ?? ''} onChange={e => setForm({ ...form, testCommand: e.target.value })} error={!!errorFor('testCommand')} helperText={errorFor('testCommand')} disabled={createMutation.isPending} />

            {/* The topology is not a detail the form can leave blank. It decides whether the
                submission and the hidden suite share one sandbox, and the two produce
                different marks for the same work, so the server refuses a runtime that does
                not state it. */}
            <FormControl fullWidth required error={!!errorFor('shimKind')}>
              <InputLabel id="shim-kind-label">Grading Topology</InputLabel>
              <Select
                labelId="shim-kind-label"
                label="Grading Topology"
                value={form.shimKind ?? ''}
                onChange={e => setForm({ ...form, shimKind: e.target.value })}
                disabled={createMutation.isPending}
              >
                <MenuItem value={LEGACY_TOPOLOGY}>Single sandbox (legacy)</MenuItem>
                {SHIM_KINDS.map(kind => (
                  <MenuItem key={kind} value={kind}>Shim: {kind}</MenuItem>
                ))}
              </Select>
              <FormHelperText>
                {errorFor('shimKind') ?? 'A shimmed runtime needs a hidden suite that imports the shim client; without one it cannot load the submission.'}
              </FormHelperText>
            </FormControl>
            <TextField label="Shim Command (Optional)" fullWidth value={form.shimCommand ?? ''} onChange={e => setForm({ ...form, shimCommand: e.target.value || null })} disabled={createMutation.isPending || form.shimKind === LEGACY_TOPOLOGY} helperText="Leave empty to use the command baked into the runtime image." />

            {/* Free text here defaulted to `JUNIT`, which the server does not accept, so
                the prefilled form was rejected on submit and no runtime could be added
                without guessing the exact spelling of a value it never showed. */}
            <FormControl fullWidth required error={!!errorFor('reportFormat')}>
              <InputLabel id="report-format-label">Report Format</InputLabel>
              <Select
                labelId="report-format-label"
                label="Report Format"
                value={form.reportFormat ?? ''}
                onChange={e => setForm({ ...form, reportFormat: e.target.value })}
                disabled={createMutation.isPending}
              >
                {REPORT_FORMATS.map(format => (
                  <MenuItem key={format} value={format}>{format}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControlLabel control={<Checkbox checked={form.enabled ?? false} onChange={e => setForm({ ...form, enabled: e.target.checked })} disabled={createMutation.isPending} />} label="Enabled" />
          </DialogContent>
          <DialogActions>
            <Button onClick={closeDialog} disabled={createMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createMutation.isPending}>Create Runtime</Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
}