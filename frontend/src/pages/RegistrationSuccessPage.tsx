// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useLocation, Navigate } from 'react-router';
import { Box, Typography, Paper, Button, List, ListItem, ListItemText } from '@mui/material';
import type { RegistrationResponse } from '../api';

/**
 * Copies one clone command, and admits it when it could not.
 *
 * The clipboard API does not exist on an insecure origin and can be refused on a secure
 * one, and both arrive here as a click that did nothing at all. This is the first page a
 * student sees and the clone URL is the one thing they came away with, so a button that
 * quietly fails sends them looking for a command they think they already have.
 */
function CopyCloneCommand({ command }: { command: string }) {
  const [outcome, setOutcome] = useState<'idle' | 'copied' | 'failed'>('idle');

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(command);
      setOutcome('copied');
    }
    catch {
      setOutcome('failed');
    }
  };

  const label = outcome === 'copied' ? 'Copied' : outcome === 'failed' ? 'Copy it by hand' : 'Copy';
  return (
    <Button size="small" onClick={() => void copy()} aria-live="polite">
      {label}
    </Button>
  );
}

export function RegistrationSuccessPage() {
  const location = useLocation();
  const result = location.state?.result as RegistrationResponse | undefined;

  if (!result) return <Navigate to="/register" replace />;

  return (
    <Box sx={{ p: 4, maxWidth: 'md', mx: 'auto' }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>Registration Successful</Typography>
        <Typography component="p" sx={{ mb: 2 }}>Welcome, {result.fullName}. Your SSH key fingerprint is {result.keyFingerprint}.</Typography>
        <Typography variant="h6" gutterBottom>Your Assignments</Typography>
        <List>
          {result.repositories.map(repo => (
            <ListItem key={repo.assignmentKey}>
              <ListItemText 
                primary={repo.assignmentTitle} 
                secondary={
                  <Box component="span" sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                    <Box
                      component="code"
                      // A clone URL is longer than a phone is wide, and this is the first
                      // page a student sees after registering.
                      sx={{
                        bgcolor: 'action.hover',
                        px: 0.5,
                        py: 0.25,
                        borderRadius: 1,
                        fontFamily: 'monospace',
                        overflowWrap: 'anywhere',
                        minWidth: 0
                      }}
                    >
                      git clone {repo.cloneUrl}
                    </Box>
                    <CopyCloneCommand command={`git clone ${repo.cloneUrl}`} />
                  </Box>
                } 
              />
            </ListItem>
          ))}
        </List>
      </Paper>
    </Box>
  );
}
