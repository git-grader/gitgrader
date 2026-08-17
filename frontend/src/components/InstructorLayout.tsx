// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { Outlet, Navigate, Link, useLocation } from 'react-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { ApiProblem } from '../api/client';
import { QueryErrorNotice } from './QueryErrorNotice';
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

/**
 * Whether the failure means the session is over rather than the server is unwell.
 *
 * Treating every error as an expired session signed an instructor out on a dropped
 * connection or a 500, discarding whatever they had typed. Only the two answers that
 * actually say "not you" send them back to sign in.
 */
function isSessionRefusal(error: unknown): boolean {
  return error instanceof ApiProblem && (error.status === 401 || error.status === 403);
}

export function InstructorLayout() {
  const meta = useMeta();
  const location = useLocation();
  const queryClient = useQueryClient();
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const { data: me, isPending, error, refetch } = useQuery({
    queryKey: queryKeys.me,
    queryFn: api.getMe,
    retry: false
  });

  if (isPending) {
    return (
      <Box sx={{ p: 4 }}>
        <CircularProgress aria-label="Signing you in" />
      </Box>
    );
  }
  if (error) {
    if (isSessionRefusal(error)) {
      return <Navigate to="/login" state={{ from: location }} replace />;
    }
    return <QueryErrorNotice message="GitGrader could not be reached." onRetry={() => void refetch()} />;
  }

  const isAdmin = me.roles.includes('ROLE_ADMIN');

  /**
   * Ends the session and takes the previous user's data with it.
   *
   * The navigation replaces the document rather than pushing a route, because that is
   * what discards the cache: a router push left every student record, submission and
   * audit entry the previous user had loaded sitting in the query cache, readable by
   * whoever signed in next. Clearing it explicitly as well means the gap between the two
   * is not a window either.
   */
  async function signOut() {
    try {
      await api.logout();
    }
    catch {
      // A session that is already gone answers with an error, and stranding the user on
      // a page they can no longer load would be worse than sending them to sign in.
    }
    finally {
      queryClient.clear();
      window.location.assign('/login');
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
      {/* The drawer repeats every link on every page, so a keyboard or screen-reader
          user met the whole navigation again before reaching the content each time. */}
      <Box
        component="a"
        href="#main-content"
        onClick={(event: React.MouseEvent<HTMLAnchorElement>) => {
          event.preventDefault();
          document.getElementById('main-content')?.focus();
        }}
        sx={{
          position: 'absolute',
          width: 1,
          height: 1,
          overflow: 'hidden',
          clip: 'rect(0 0 0 0)',
          whiteSpace: 'nowrap',
          zIndex: (t) => t.zIndex.tooltip + 1,
          '&:focus': {
            clip: 'auto',
            width: 'auto',
            height: 'auto',
            overflow: 'visible',
            top: 8,
            left: 8,
            px: 2,
            py: 1,
            borderRadius: 1,
            bgcolor: 'background.paper',
            color: 'text.primary',
            border: 1,
            borderColor: 'divider'
          }
        }}
      >
        Skip to main content
      </Box>
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
        id="main-content"
        tabIndex={-1}
        sx={{
          flexGrow: 1,
          // Without this a flex child refuses to shrink below the intrinsic width of
          // its content, so a wide table pushed the whole page sideways rather than
          // scrolling within itself.
          minWidth: 0,
          p: { xs: 2, md: 3 },
          '&:focus': { outline: 'none' }
        }}
      >
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
