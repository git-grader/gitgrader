// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Outlet, Navigate, Link, useLocation } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { Box, Drawer, List, ListItem, ListItemButton, ListItemText, AppBar, Toolbar, Typography, CircularProgress } from '@mui/material';
import { useMeta } from './MetaProvider';

const DRAWER_WIDTH = 240;

export function InstructorLayout() {
  const meta = useMeta();
  const location = useLocation();
  const { data: me, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: api.getMe,
    retry: false
  });

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress /></Box>;
  if (error) return <Navigate to="/login" state={{ from: location }} replace />;
  if (!me) return null;

  const isAdmin = me.roles.includes('ROLE_ADMIN');

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" noWrap component="div">
            {meta.name} - Instructor
          </Typography>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: 'auto' }}>
          <List component="nav">
            <ListItem disablePadding><ListItemButton component={Link} to="/dashboard"><ListItemText primary="Dashboard" /></ListItemButton></ListItem>
            <ListItem disablePadding><ListItemButton component={Link} to="/students"><ListItemText primary="Students" /></ListItemButton></ListItem>
            <ListItem disablePadding><ListItemButton component={Link} to="/courses"><ListItemText primary="Courses" /></ListItemButton></ListItem>
            <ListItem disablePadding><ListItemButton component={Link} to="/assignments"><ListItemText primary="Assignments" /></ListItemButton></ListItem>
            <ListItem disablePadding><ListItemButton component={Link} to="/submissions"><ListItemText primary="Submissions" /></ListItemButton></ListItem>
            {/* Note: Server enforces authorization independently. Hiding UI is convenience. */}
            {isAdmin && (
              <>
                <ListItem disablePadding><ListItemButton component={Link} to="/admin/audit"><ListItemText primary="Audit Log" /></ListItemButton></ListItem>
                <ListItem disablePadding><ListItemButton component={Link} to="/admin/settings"><ListItemText primary="Settings" /></ListItemButton></ListItem>
                <ListItem disablePadding><ListItemButton component={Link} to="/admin/runtimes"><ListItemText primary="Runtimes" /></ListItemButton></ListItem>
              </>
            )}
          </List>
        </Box>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
