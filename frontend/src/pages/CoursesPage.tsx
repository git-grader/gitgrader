// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Box, Typography, CircularProgress, List, ListItem, ListItemText, Paper } from '@mui/material';

export function CoursesPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['availability'],
    queryFn: () => api.getAvailability()
  });

  if (isLoading) return <Box p={4}><CircularProgress /></Box>;
  if (!data) return null;

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Courses (from Availability)</Typography>
      <Paper sx={{ p: 2 }}>
        <List>
          {data.courses.map(c => (
            <ListItem key={c.courseKey}>
              <ListItemText 
                primary={c.name} 
                secondary={`Key: ${c.courseKey} | Classes: ${c.classes.length}`} 
              />
            </ListItem>
          ))}
        </List>
      </Paper>
    </Box>
  );
}
