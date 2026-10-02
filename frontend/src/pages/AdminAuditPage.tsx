// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useSearchParams } from 'react-router';
import { Box, Chip, CircularProgress, FormControl, InputLabel, MenuItem, Select, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { InstructorDataGrid } from '../components/InstructorDataGrid';
import type { GridColDef, GridRenderCellParams } from '@mui/x-data-grid';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { PageHeader } from '../components/PageHeader';
import { tablePageSx, tablePanelSx } from '../components/pageLayout';
import { useServerPagination } from '../components/useServerPagination';
import type { AuditEvent } from '../api';

const AUDIT_EVENT_TYPES = [
  'STUDENT_REGISTERED', 'STUDENT_VERIFIED', 'STUDENT_SUSPENDED', 'STUDENT_ARCHIVED',
  'STUDENT_ANONYMIZED', 'SSH_KEY_ADDED', 'SSH_KEY_REPLACED', 'SSH_KEY_REVOKED',
  'SSH_KEY_REINSTATED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGOUT', 'COURSE_CHANGED',
  'ASSIGNMENT_CHANGED', 'ASSIGNMENT_PUBLISHED', 'DEADLINE_CHANGED', 'EXTENSION_GRANTED',
  'EXTENSION_REVOKED', 'TEMPLATE_PUBLISHED', 'TEST_SUITE_PUBLISHED', 'RUNTIME_CHANGED',
  'REPOSITORY_PROVISIONED', 'SUBMISSION_RECEIVED', 'SUBMISSION_REJECTED', 'GRADING_COMPLETED',
  'GRADING_RETRIED', 'RESULT_TOKEN_REVOKED', 'REPORT_EXPORTED', 'SETTING_CHANGED',
  'RATE_LIMIT_TRIGGERED'
] as const;

const AUDIT_ACTOR_TYPES = ['STUDENT', 'INSTRUCTOR', 'ADMIN', 'SYSTEM', 'ANONYMOUS'] as const;

const SEVERITY_COLOR: Record<string, 'default' | 'info' | 'warning' | 'error'> = {
  INFO: 'default',
  NOTICE: 'info',
  WARNING: 'warning',
  CRITICAL: 'error'
};

/**
 * Renders the detail object as `key=value` pairs.
 *
 * The column is deliberately plain text. Audit detail is arbitrary JSON written by
 * whichever call site raised the event, so anything that assumed a fixed shape would
 * silently show nothing the first time a new event type was recorded. A nested value is
 * serialised rather than stringified, because `String({})` reads `[object Object]` and
 * hides exactly the detail the reader opened this page for.
 */
function summariseDetail(detail: unknown): string {
  if (!detail || typeof detail !== 'object') return '';
  return Object.entries(detail)
    .filter(([, value]) => value !== null && value !== undefined)
    .map(([key, value]) => `${key}=${typeof value === 'object' ? JSON.stringify(value) : String(value)}`)
    .join('  ');
}

export function AdminAuditPage() {
  const theme = useTheme();
  const { paginationModel, setPaginationModel, sortModel, setSortModel, params } = useServerPagination();
  const [searchParams, setSearchParams] = useSearchParams();
  const eventType = searchParams.get('eventType') ?? '';
  const actorType = searchParams.get('actorType') ?? '';
  function setFilter(key: string, value: string) {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value); else next.delete(key);
    setSearchParams(next);
  }
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.audit(params.page, params.size, eventType, actorType, params.sort),
    queryFn: () =>
      api.getAuditLog({
        ...params,
        ...(eventType ? { eventType } : {}),
        ...(actorType ? { actorType } : {})
      }),
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
    renderCell: (params: GridRenderCellParams<AuditEvent>) => {
      const row = params.row;
      return (
        <Box sx={{ py: 1, display: 'flex', flexDirection: 'column', gap: 0.5, minWidth: 0 }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Chip size="small" color={SEVERITY_COLOR[row.severity] ?? 'default'} label={row.severity} />
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{row.eventType}</Typography>
          </Box>
          <Typography variant="caption" color="text.secondary">
            {new Date(row.occurredAt).toLocaleString()} · {row.outcome} · {row.actorType}
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
      renderCell: (params: GridRenderCellParams<AuditEvent>) => (
        <Chip size="small" color={SEVERITY_COLOR[params.row.severity] ?? 'default'} label={params.row.severity} />
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
      // recoverable without widening the grid past the viewport - and it hangs off a
      // focusable element, because a tooltip only a mouse can open is not reachable at
      // all for anyone using the keyboard.
      renderCell: (params: GridRenderCellParams<AuditEvent, string>) => (
        <Tooltip title={params.value ?? ''}>
          <Box
            component="span"
            tabIndex={0}
            sx={{ display: 'block', maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
          >
            {params.value ?? ''}
          </Box>
        </Tooltip>
      ),
      sortable: false
    }
  ];

  return (
    <Box sx={tablePageSx}>
      <PageHeader title="Audit Log" />
      <Typography variant="body2" color="text.secondary" sx={{ maxWidth: '80ch' }}>
        Every recorded action, newest first. Throttling decisions appear as
        <Box component="code" sx={{ mx: 0.5 }}>RATE_LIMIT_TRIGGERED</Box>
        with the limit and the decision recorded alongside.
      </Typography>
      <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ width: { xs: '100%', sm: 'auto' }, minWidth: { sm: 220 } }}>
          <InputLabel id="audit-event-type-label">Event Type</InputLabel>
          <Select
            labelId="audit-event-type-label"
            value={eventType}
            label="Event Type"
            onChange={(e) => { setFilter('eventType', e.target.value); setPaginationModel({ ...paginationModel, page: 0 }); }}
          >
            <MenuItem value=""><em>All events</em></MenuItem>
            {AUDIT_EVENT_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ width: { xs: '100%', sm: 'auto' }, minWidth: { sm: 180 } }}>
          <InputLabel id="audit-actor-type-label">Actor Type</InputLabel>
          <Select
            labelId="audit-actor-type-label"
            value={actorType}
            label="Actor Type"
            onChange={(e) => { setFilter('actorType', e.target.value); setPaginationModel({ ...paginationModel, page: 0 }); }}
          >
            <MenuItem value=""><em>All actors</em></MenuItem>
            {AUDIT_ACTOR_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
          </Select>
        </FormControl>
      </Box>

      {/* The error used to be shown above the grid, which then rendered anyway: a reader
          got a failure notice and an empty table at once, and no way to try again. */}
      {isError ? (
        <QueryErrorNotice message="The audit log could not be loaded." onRetry={() => void refetch()} />
      ) : isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress aria-label="Loading audit log" />
        </Box>
      ) : (
        <Box sx={tablePanelSx}>
          <InstructorDataGrid
            rows={data?.content ?? []}
            columns={isNarrow ? [summaryColumn] : wideColumns}
            {...(isNarrow ? { getRowHeight: () => 'auto' as const } : {})}
            paginationMode="server"
            rowCount={data?.totalElements ?? 0}
            paginationModel={paginationModel}
            onPaginationModelChange={setPaginationModel}
            pageSizeOptions={[20, 50, 100]}
            sortingMode="server"
            sortModel={sortModel}
            onSortModelChange={setSortModel}
            disableRowSelectionOnClick
            sx={{ minWidth: isNarrow ? 260 : 940 }}
          />
        </Box>
      )}
    </Box>
  );
}
