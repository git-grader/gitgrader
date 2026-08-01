// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Chip, CircularProgress, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import type { GridColDef } from '@mui/x-data-grid';
import { api } from '../api';

const SEVERITY_COLOR: Record<string, 'default' | 'info' | 'warning' | 'error'> = {
  INFO: 'default',
  NOTICE: 'info',
  WARNING: 'warning',
  CRITICAL: 'error'
};

/**
 * Renders the flat detail object as `key=value` pairs.
 *
 * The column is deliberately plain text. Audit detail is arbitrary JSON written by
 * whichever call site raised the event, so anything that assumed a fixed shape would
 * silently show nothing the first time a new event type was recorded.
 */
function summariseDetail(detail: unknown): string {
  if (!detail || typeof detail !== 'object') return '';
  return Object.entries(detail as Record<string, unknown>)
    .filter(([, value]) => value !== null && value !== undefined)
    .map(([key, value]) => `${key}=${String(value)}`)
    .join('  ');
}

interface AuditRow {
  readonly eventType: string;
  readonly severity: string;
  readonly occurredAt: string;
  readonly outcome?: string | null;
  readonly actorType: string;
  readonly detail?: unknown;
}

export function AdminAuditPage() {
  const theme = useTheme();
  const [pagination, setPagination] = useState({ page: 0, pageSize: 20 });
  const { data, isLoading, error } = useQuery({
    queryKey: ['audit', pagination.page, pagination.pageSize],
    queryFn: () =>
      api.getAuditLog({ page: String(pagination.page), size: String(pagination.pageSize) }),
    retry: false,
    // Without this the row count drops to zero while the next page loads and the grid
    // resets itself to the first page, making paging past page one impossible.
    placeholderData: (previous) => previous
  });

  const isNarrow = useMediaQuery(theme.breakpoints.down('md'));

  /**
   * One stacked cell per event, used instead of columns on a narrow screen.
   *
   * Hiding the lower-priority columns kept the grid inside the viewport but removed the
   * outcome and the detail, which are the only reason to open this page: an event
   * without its decision says nothing. Stacking them keeps every field reachable
   * without a horizontal scroll.
   */
  const summaryColumn: GridColDef = {
    field: 'eventType',
    headerName: 'Event',
    flex: 1,
    minWidth: 240,
    sortable: false,
    renderCell: (params) => {
      const row = params.row as AuditRow;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip size="small" color={SEVERITY_COLOR[row.severity] ?? 'default'} label={row.severity} />
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{row.eventType}</Typography>
          </Box>
          <Typography variant="caption" color="text.secondary">
            {new Date(row.occurredAt).toLocaleString()} · {row.outcome ?? '—'} · {row.actorType}
          </Typography>
          {summariseDetail(row.detail) && (
            <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              {summariseDetail(row.detail)}
            </Typography>
          )}
        </Box>
      );
    }
  };

  const wideColumns: GridColDef[] = [
    {
      field: 'occurredAt',
      headerName: 'When',
      width: 175,
      valueGetter: (value: string) => (value ? new Date(value).toLocaleString() : '')
    },
    { field: 'eventType', headerName: 'Event', flex: 1, minWidth: 150 },
    {
      field: 'severity',
      headerName: 'Severity',
      width: 120,
      renderCell: (params) => (
        <Chip size="small" color={SEVERITY_COLOR[String(params.value)] ?? 'default'} label={String(params.value)} />
      )
    },
    { field: 'outcome', headerName: 'Outcome', width: 110 },
    { field: 'actorType', headerName: 'Actor', width: 110 },
    {
      field: 'detail',
      headerName: 'Detail',
      flex: 1,
      minWidth: 260,
      valueGetter: (value: unknown) => summariseDetail(value),
      // Detail is the widest and least predictable column, so the cell is narrower than
      // its content more often than not. The tooltip is what makes the truncated value
      // recoverable without widening the grid past the viewport.
      renderCell: (params) => (
        <Tooltip title={String(params.value ?? '')}>
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {String(params.value ?? '')}
          </span>
        </Tooltip>
      ),
      sortable: false
    }
  ];

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Audit Log</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        Every recorded action, newest first. Throttling decisions appear as
        <code style={{ margin: '0 4px' }}>RATE_LIMIT_TRIGGERED</code>
        with the limit and the decision recorded alongside.
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }}>The audit log could not be loaded.</Alert>}

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress /></Box>
      ) : (
        <Box sx={{ height: 600, width: '100%' }}>
          <DataGrid
            rows={data?.content ?? []}
            columns={isNarrow ? [summaryColumn] : wideColumns}
            {...(isNarrow ? { getRowHeight: () => 'auto' as const } : {})}
            paginationMode="server"
            rowCount={data?.totalElements ?? 0}
            paginationModel={pagination}
            onPaginationModelChange={setPagination}
            pageSizeOptions={[20, 50, 100]}
            loading={isLoading}
            // Only the requested page is in memory, so a client-side sort would silently
            // reorder that page alone while appearing to sort the whole log. The server
            // already returns it newest first.
            disableColumnSorting
            disableRowSelectionOnClick
          />
        </Box>
      )}
    </Box>
  );
}
