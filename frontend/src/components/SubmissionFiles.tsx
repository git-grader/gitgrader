// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  CircularProgress,
  Divider,
  List,
  ListItem,
  ListItemButton,
  Paper,
  Tooltip,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { api } from '../api';
import type { SubmissionContentEntry } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from './QueryErrorNotice';

/**
 * Navigable file tree + viewer for one submission's committed files.
 * Only the text content of the graded commit is shown; binary files get a
 * placeholder. This component is intentionally read-only: an instructor can
 * review the submitted code but never change it.
 */
export function SubmissionFiles({ submissionId }: { submissionId: string }) {
  const [selectedPath, setSelectedPath] = useState<string | null>(null);

  const treeQuery = useQuery({
    queryKey: queryKeys.submissions.files(submissionId, ''),
    queryFn: () => api.getSubmissionFiles(submissionId),
  });

  if (treeQuery.isLoading) {
    return <CircularProgress aria-label="Loading file tree" />;
  }
  if (treeQuery.isError) {
    return (
      <QueryErrorNotice
        message="File tree could not be loaded."
        onRetry={() => void treeQuery.refetch()}
      />
    );
  }

  const entries = treeQuery.data ?? [];
  if (entries.length === 0) {
    return (
      <Alert severity="info">
        No files were found at the graded commit of this submission.
      </Alert>
    );
  }

  return (
    <Box
      component="section"
      aria-labelledby="submission-files-heading"
      sx={{ display: 'flex', gap: 2, minHeight: 400 }}
    >
      <Typography id="submission-files-heading" sx={{ display: 'none' }}>
        Submitted files
      </Typography>

      {/* ── Tree panel ─────────────────────────────────────────── */}
      <Paper
        variant="outlined"
        sx={{ width: 240, minWidth: 0, flexShrink: 0, overflow: 'auto', maxHeight: 600 }}
      >
        <Box sx={{ p: 1, borderBottom: 1, borderColor: 'divider' }}>
          <Typography variant="caption" color="text.secondary">
            {submissionId.slice(0, 8)}&hellip; /
          </Typography>
        </Box>
        <List dense disablePadding>
          {entries.map((entry) => (
            <FileTreeItem
              key={entry.path}
              entry={entry}
              selectedPath={selectedPath}
              onSelect={setSelectedPath}
            />
          ))}
        </List>
      </Paper>

      {/* ── Content panel ─────────────────────────────────────── */}
      <Paper
        variant="outlined"
        sx={{ flex: 1, minWidth: 0, overflow: 'auto', maxHeight: 600 }}
      >
        <FileContent submissionId={submissionId} selectedPath={selectedPath} />
      </Paper>
    </Box>
  );
}

/** One row in the file tree sidebar. Files open for viewing. */
function FileTreeItem({
  entry,
  selectedPath,
  onSelect,
}: {
  entry: SubmissionContentEntry;
  selectedPath: string | null;
  onSelect: (path: string) => void;
}) {
  const isFile = entry.type === 'file';
  const isSelected = selectedPath === entry.path;

  if (!isFile) {
    return (
      <ListItem disablePadding>
        <ListItemButton sx={{ py: 0.25, pl: 1 }}>
          <FileIcon entry={entry} />
          <Typography
            variant="body2"
            sx={{ ml: 0.5, fontFamily: 'monospace', fontSize: '0.75rem', fontWeight: 600 }}
          >
            {entry.name}
          </Typography>
        </ListItemButton>
      </ListItem>
    );
  }

  return (
    <ListItem disablePadding>
      <ListItemButton
        selected={isSelected}
        onClick={() => onSelect(entry.path)}
        sx={{ py: 0.25, pl: 1 }}
      >
        <FileIcon entry={entry} />
        <Typography
          variant="body2"
          sx={{ ml: 0.5, fontFamily: 'monospace', fontSize: '0.75rem' }}
        >
          {entry.name}
        </Typography>
      </ListItemButton>
    </ListItem>
  );
}

function FileIcon({ entry }: { entry: SubmissionContentEntry }) {
  if (entry.type === 'dir') {
    return <span aria-hidden="true" style={{ fontSize: '0.9rem' }}>📁</span>;
  }
  if (entry.binary) {
    return <span aria-hidden="true" style={{ fontSize: '0.9rem' }}>🖼️</span>;
  }
  const ext = entry.name.split('.').pop() ?? '';
  const short = ext.length > 0 ? ext : '?';
  return (
    <Tooltip title={entry.name}>
      <span
        aria-hidden="true"
        style={{
          fontSize: '0.6rem',
          fontFamily: 'monospace',
          backgroundColor: 'var(--mui-palette-grey-200)',
          color: 'var(--mui-palette-grey-800)',
          borderRadius: 3,
          padding: '0 4px',
          lineHeight: 1.4,
        }}
      >
        {short.slice(0, 3)}
      </span>
    </Tooltip>
  );
}

function FileContent({
  submissionId,
  selectedPath,
}: {
  submissionId: string;
  selectedPath: string | null;
}) {
  const contentQuery = useQuery({
    queryKey: queryKeys.submissions.files(submissionId, selectedPath ?? ''),
    queryFn: () => api.getSubmissionFileContent(submissionId, selectedPath ?? ''),
    enabled: !!selectedPath,
  });

  if (!selectedPath) {
    return (
      <Box
        sx={{
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'text.secondary',
        }}
      >
        <Typography variant="body2">Select a file to view its content</Typography>
      </Box>
    );
  }

  if (contentQuery.isLoading) {
    return <CircularProgress aria-label="Loading file content" />;
  }
  if (contentQuery.isError || contentQuery.data === undefined) {
    return (
      <QueryErrorNotice
        message="File content could not be loaded."
        onRetry={() => void contentQuery.refetch()}
      />
    );
  }

  const file = contentQuery.data;

  if (file.binary) {
    return (
      <Box sx={{ p: 2 }}>
        <Typography variant="body2" color="text.secondary">
          Binary file — content is not shown
        </Typography>
        <Divider sx={{ my: 1 }} />
        <Typography variant="caption" color="text.secondary">
          {file.path}
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box
        sx={{
          p: 1,
          borderBottom: 1,
          borderColor: 'divider',
          backgroundColor: 'grey.50',
          flexShrink: 0,
        }}
      >
        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
          {file.path}
        </Typography>
      </Box>
      <Box
        component="pre"
        sx={{
          m: 0,
          p: 2,
          flex: 1,
          overflow: 'auto',
          fontFamily: '"Fira Code", "Cascadia Code", Consolas, monospace',
          fontSize: '0.8rem',
          lineHeight: 1.6,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {file.content ?? ''}
      </Box>
    </Box>
  );
}
