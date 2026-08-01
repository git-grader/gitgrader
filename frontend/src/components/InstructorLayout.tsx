// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { Outlet, Navigate, Link, useLocation, useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import {
  Box,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  AppBar,
  Toolbar,
  Typography,
  CircularProgress,
  IconButton,
  Button,
  Tooltip,
  useTheme,
  useMediaQuery
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import LogoutIcon from '@mui/icons-material/Logout';
import { useMeta } from './MetaProvider';
import PrimaryMark from '../assets/brand/gitgrader-mark-primary.svg';
import ReversedMark from '../assets/brand/gitgrader-mark-reversed.svg';

const DRAWER_WIDTH = 240;

interface NavItem {
  readonly label: string;
  readonly to: string;
}

const MAIN_NAV: readonly NavItem[] = [
  { label: 'Dashboard', to: '/dashboard' },
  { label: 'Students', to: '/students' },
  { label: 'Courses', to: '/courses' },
  { label: 'Assignments', to: '/assignments' },
  { label: 'Materials', to: '/materials' },
  { label: 'Submissions', to: '/submissions' }
];

const ADMIN_NAV: readonly NavItem[] = [
  { label: 'Audit Log', to: '/admin/audit' },
  { label: 'Settings', to: '/admin/settings' },
  { label: 'Runtimes', to: '/admin/runtimes' }
];

export function InstructorLayout() {
  const meta = useMeta();
  const location = useLocation();
  const navigate = useNavigate();
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const { data: me, isLoading, error } = useQuery({
    queryKey: ['me'],
    queryFn: api.getMe,
    retry: false
  });

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress /></Box>;
  if (error) return <Navigate to="/login" state={{ from: location }} replace />;
  if (!me) return null;

  const isAdmin = me.roles.includes('ROLE_ADMIN');

  async function signOut() {
    try {
      await api.logout();
    }
    finally {
      // Navigating regardless of the response: if the session is already gone the
      // server answers an error, and stranding the user on a page they can no longer
      // load would be worse than sending them to sign in again.
      await navigate('/login', { replace: true });
    }
  }

  function isCurrent(to: string): boolean {
    return location.pathname === to || location.pathname.startsWith(`${to}/`);
  }

  const navigation = (
    <Box sx={{ overflow: 'auto' }}>
      <List component="nav">
        {MAIN_NAV.concat(isAdmin ? ADMIN_NAV : []).map((item) => (
          <ListItem key={item.to} disablePadding>
            <ListItemButton
              component={Link}
              to={item.to}
              selected={isCurrent(item.to)}
              aria-current={isCurrent(item.to) ? 'page' : undefined}
              onClick={() => { setMobileOpen(false); }}
            >
              <ListItemText primary={item.label} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar
        position="fixed"
        sx={{
          zIndex: (t) => t.zIndex.drawer + 1,
          backgroundColor: 'background.paper',
          borderBottom: 1,
          borderColor: 'divider',
          color: 'text.primary',
          boxShadow: 'none'
        }}
      >
        <Toolbar>
          {!isDesktop && (
            <IconButton
              edge="start"
              onClick={() => { setMobileOpen((open) => !open); }}
              aria-label="Open navigation"
              sx={{ mr: 1 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Box
            component="img"
            src={theme.palette.mode === 'dark' ? ReversedMark : PrimaryMark}
            alt="GitGrader"
            sx={{ height: 24, mr: { xs: 1.5, md: 3 } }}
          />
          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1, minWidth: 0 }}>
            {isDesktop
              ? (meta.organizationName ? `${meta.organizationName} - Instructor` : 'Instructor')
              : (meta.organizationName || 'Instructor')}
          </Typography>
          {isDesktop ? (
            <Button
              color="inherit"
              startIcon={<LogoutIcon />}
              onClick={() => { void signOut(); }}
            >
              Sign out
            </Button>
          ) : (
            <Tooltip title="Sign out">
              <IconButton color="inherit" aria-label="Sign out" onClick={() => { void signOut(); }}>
                <LogoutIcon />
              </IconButton>
            </Tooltip>
          )}
        </Toolbar>
      </AppBar>

      {/* A permanent drawer would take 240 of the 390 pixels a phone has, which left
          every page overflowing sideways. Below md it becomes an overlay opened from
          the toolbar instead. */}
      <Drawer
        variant={isDesktop ? 'permanent' : 'temporary'}
        open={isDesktop || mobileOpen}
        onClose={() => { setMobileOpen(false); }}
        ModalProps={{ keepMounted: true }}
        sx={{
          width: { md: DRAWER_WIDTH },
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' }
        }}
      >
        <Toolbar />
        {navigation}
      </Drawer>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          // Without this a flex child refuses to shrink below the intrinsic width of
          // its content, so a wide table pushed the whole page sideways rather than
          // scrolling within itself.
          minWidth: 0,
          p: { xs: 2, md: 3 }
        }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
