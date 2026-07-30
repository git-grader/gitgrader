// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Box, Typography, CircularProgress, List, ListItem, ListItemText } from '@mui/material';

export function AdminRuntimesPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['runtimes'],
    queryFn: () => api.getRuntimes()
  });

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress /></Box>;
  if (!data) return null;

  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>Runtimes</Typography>
      <List>
        {data.map(rt => (
          <ListItem key={rt.id}>
            <ListItemText primary={rt.name} secondary={rt.image} />
          </ListItem>
        ))}
      </List>
    </Box>
  );
}
