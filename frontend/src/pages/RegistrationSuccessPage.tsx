// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useLocation, Navigate } from 'react-router';
import { Box, Typography, Paper, Button, List, ListItem, ListItemText } from '@mui/material';
import type { RegistrationResponse } from '../api';

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
                  <Box component="span" sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <code style={{ background: '#eee', padding: '2px 4px', borderRadius: '4px', color: '#000' }}>
                      git clone {repo.cloneUrl}
                    </code>
                    <Button size="small" onClick={() => { void navigator.clipboard.writeText(`git clone ${repo.cloneUrl}`); }}>Copy</Button>
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
