// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Outlet } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Alert, AlertTitle, Box, CircularProgress } from '@mui/material';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from './QueryErrorNotice';

/**
 * Keeps the administrator pages to administrators.
 *
 * Hiding the navigation links was the whole of the check before, so an instructor who
 * typed `/admin/audit` got the page, a 403 from the server and "The audit log could not
 * be loaded" - which reads as a broken deployment rather than as a refusal, and sends
 * them to report an outage that is not happening.
 */
export function RequireAdmin() {
  const { data: me, isPending, isError, refetch } = useQuery({
    queryKey: queryKeys.me,
    queryFn: api.getMe
  });

  if (isPending) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress aria-label="Checking your access" />
      </Box>
    );
  }

  if (isError) {
    return <QueryErrorNotice message="Your access could not be checked." onRetry={() => void refetch()} />;
  }

  if (!me.roles.includes('ROLE_ADMIN')) {
    return (
      <Alert severity="warning" role="alert" sx={{ m: 2 }}>
        <AlertTitle>Administrators only</AlertTitle>
        This page is restricted to administrators. Your account is signed in as
        {' '}{me.displayName}, which does not have that role. Ask an administrator if you
        need access.
      </Alert>
    );
  }

  return <Outlet />;
}
