// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useMemo, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router';
import { DataGrid, useGridApiRef } from '@mui/x-data-grid';
import type { DataGridProps, GridSortModel, GridValidRowModel } from '@mui/x-data-grid';
import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, TextField, Typography } from '@mui/material';
import { defaultTableSettings, savedFilterParams, useTableSettings } from './tablePreferences';

export function InstructorDataGrid<R extends GridValidRowModel>(props: DataGridProps<R>) {
  const { pathname } = useLocation();
  const internalApiRef = useGridApiRef();
  const apiRef = props.apiRef ?? internalApiRef;
  const [params, setParams] = useSearchParams();
  const { settings, save } = useTableSettings(pathname, props.paginationMode === 'server' ? 20 : 100);
  const [optionsOpen, setOptionsOpen] = useState(false);
  const [saveOpen, setSaveOpen] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [pagination, setPagination] = useState({ page: 0, pageSize: settings.model.pageSize });
  function applyModels(next: typeof settings.model) {
    setPagination({ page: 0, pageSize: next.pageSize });
    if (apiRef.current) {
      const details = { api: apiRef.current, apiRef: { current: apiRef.current } };
      props.onSortModelChange?.(next.sort, details);
      props.onPaginationModelChange?.({ page: 0, pageSize: next.pageSize }, details);
    }
  }
  // MUI uses sort-model identity to reset pagination. Preserve that identity when
  // saving density, columns or page size leaves the sort unchanged.
  const serializedSort = JSON.stringify(props.sortModel ?? settings.model.sort.filter(item => props.columns.some(column => column.field === item.field)));
  const currentSort = useMemo(() => JSON.parse(serializedSort) as GridSortModel, [serializedSort]);
  const model = { ...settings.model, sort: currentSort.flatMap(item => item.sort ? [{ field: item.field, sort: item.sort }] : []) };
  function applyView(index: number) {
    const view = settings.views[index];
    if (!view) return;
    save({ ...settings, model: view.model });
    applyModels(view.model);
    setParams(savedFilterParams(new URLSearchParams(view.params)));
  }
  return <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 0', minHeight: 0, minWidth: 0, '& > .MuiDataGrid-root': { flex: '1 1 0', minHeight: 0 } }}>
    <Box role="group" aria-label="Table view controls" sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1, p: 1, flexShrink: 0 }}>
      <TextField select size="small" label="Saved views" value="" slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
        onChange={event => applyView(Number(event.target.value))} sx={{ minWidth: 140, maxWidth: '100%' }}>
        <MenuItem value="" disabled>Choose a view</MenuItem>
        {settings.views.map((view, index) => <MenuItem key={view.name} value={index}>{view.name}</MenuItem>)}
      </TextField>
      <Button size="small" onClick={() => { setName(''); setError(''); setSaveOpen(true); }}>Save view</Button>
      <Button size="small" onClick={() => setOptionsOpen(true)}>Table options</Button>
    </Box>
    <DataGrid {...props} apiRef={apiRef} paginationModel={props.paginationModel ?? pagination} density={settings.model.density}
      columnVisibilityModel={{ ...settings.model.visibility, ...Object.fromEntries(Object.entries(props.columnVisibilityModel ?? {}).filter(([, visible]) => !visible)) }}
      onColumnVisibilityModelChange={(visibility, details) => { save({ ...settings, model: { ...model, visibility } }); props.onColumnVisibilityModelChange?.(visibility, details); }}
      sortModel={currentSort}
      onSortModelChange={(sort, details) => {
        save({ ...settings, model: { ...model, sort: sort.flatMap(item => item.sort ? [{ field: item.field, sort: item.sort }] : []) } });
        props.onSortModelChange?.(sort, details);
      }}
      initialState={{ ...props.initialState, pagination: { ...props.initialState?.pagination, paginationModel: { page: 0, pageSize: settings.model.pageSize } } }}
      onPaginationModelChange={(pagination, details) => {
        setPagination({ page: pagination.page, pageSize: [20, 50, 100].includes(pagination.pageSize) ? pagination.pageSize as 20 | 50 | 100 : 20 });
        save({ ...settings, model: { ...model, pageSize: [20, 50, 100].includes(pagination.pageSize) ? pagination.pageSize as 20 | 50 | 100 : 20 } });
        props.onPaginationModelChange?.(pagination, details);
      }}
      pageSizeOptions={props.pageSizeOptions ?? [20, 50, 100]}
    />
    <Dialog open={saveOpen} onClose={() => setSaveOpen(false)} fullWidth maxWidth="xs">
      <DialogTitle>Save table view</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 2 }}>Save filters, sorting, columns and density in this browser.</Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <TextField label="View name" value={name} onChange={event => setName(event.target.value)} fullWidth slotProps={{ htmlInput: { maxLength: 60 } }} />
      </DialogContent>
      <DialogActions><Button onClick={() => setSaveOpen(false)}>Cancel</Button><Button disabled={!name.trim()} onClick={() => {
        if (settings.views.some(view => view.name === name.trim())) { setError('Choose a different name.'); return; }
        if (settings.views.length >= 20) { setError('Remove a saved view first (maximum 20).'); return; }
        save({ ...settings, views: [...settings.views, { name: name.trim(), params: savedFilterParams(params), model }] }); setSaveOpen(false);
      }}>Save</Button></DialogActions>
    </Dialog>
    <Dialog open={optionsOpen} onClose={() => setOptionsOpen(false)} fullWidth maxWidth="sm">
      <DialogTitle>Table options</DialogTitle><DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField select label="Density" value={settings.model.density} onChange={event => {
          const density = event.target.value;
          if (density === 'compact' || density === 'standard' || density === 'comfortable') save({ ...settings, model: { ...model, density } });
        }}>{['compact', 'standard', 'comfortable'].map(density => <MenuItem key={density} value={density}>{density}</MenuItem>)}</TextField>
        <Typography component="h3" variant="subtitle2">Visible columns</Typography>
        {props.columns.map(column => <FormControlLabel key={column.field} label={column.headerName ?? column.field}
          control={<Checkbox disabled={props.columnVisibilityModel?.[column.field] === false || column.hideable === false}
            checked={props.columnVisibilityModel?.[column.field] !== false && settings.model.visibility[column.field] !== false}
            onChange={event => save({ ...settings, model: { ...model, visibility: { ...model.visibility, [column.field]: event.target.checked } } })} />} />)}
        <Typography variant="body2">Some columns use a stacked layout on small screens.</Typography>
        {settings.views.map((view, index) => <Button key={view.name} color="error" onClick={() => save({ ...settings, views: settings.views.filter((_, i) => i !== index) })}>Remove view: {view.name}</Button>)}
      </DialogContent><DialogActions>
        <Button onClick={() => { save({ ...settings, model: defaultTableSettings().model }); applyModels(defaultTableSettings().model); }}>Reset table preferences</Button>
        <Button onClick={() => setOptionsOpen(false)}>Done</Button>
      </DialogActions>
    </Dialog>
  </Box>;
}
