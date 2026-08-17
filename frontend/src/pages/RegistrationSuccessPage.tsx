// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useLocation, Navigate } from 'react-router';
import { Alert, Box, Typography, Paper, Button, List, ListItem, ListItemText } from '@mui/material';
import { RegistrationResponseSchema } from '../api';

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
    <>
      <Button size="small" onClick={() => void copy()}>
        {label}
      </Button>
      {/* The announcement lives in its own region rather than on the button. A live
          region that is also the focused control is announced inconsistently, and here
          it is the only confirmation that the command was copied at all. */}
      <Box component="span" role="status" sx={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>
        {outcome === 'copied' ? 'Clone command copied to the clipboard.' : outcome === 'failed' ? 'The clone command could not be copied. Select it and copy it by hand.' : ''}
      </Box>
    </>
  );
}

export function RegistrationSuccessPage() {
  const location = useLocation();
  // Routing state is whatever the previous page put there, and casting it meant a
  // malformed value crashed on the first field this page read rather than being
  // recognised as not a registration at all.
  const state: unknown = location.state;
  const parsed = RegistrationResponseSchema.safeParse(
    state && typeof state === 'object' && 'result' in state ? state.result : undefined
  );

  if (!parsed.success) return <Navigate to="/register" replace />;
  const result = parsed.data;

  return (
    <Box sx={{ p: 4, maxWidth: 'md', mx: 'auto' }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>Registration Successful</Typography>
        <Typography component="p" sx={{ mb: 1 }}>
          Welcome, {result.fullName}. Your student number is {result.studentNumber} and your SSH key fingerprint is {result.keyFingerprint}.
        </Typography>
        {/* The registration status decides whether pushes are accepted yet, and leaving
            it out let a student whose account still needs verifying leave this page
            believing they were ready to submit. */}
        {result.status === 'SELF_REGISTERED' && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Your registration is recorded but not yet verified by an instructor. You can clone now; ask your instructor
            if a push is refused.
          </Alert>
        )}
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
