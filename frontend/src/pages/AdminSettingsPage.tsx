// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { Box, CircularProgress, Divider, Paper, Typography } from '@mui/material';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';

export function AdminSettingsPage() {
  const query = useQuery({ queryKey: queryKeys.meta, queryFn: api.getMeta });
  if (query.isLoading) return <CircularProgress aria-label="Loading settings" />;
  if (query.isError || !query.data) return <QueryErrorNotice message="Deployment settings could not be loaded." onRetry={() => void query.refetch()} />;
  const meta = query.data;
  const setting = (label: string, value: string) => <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value || '—'}</Typography></Box>;
  return <Box sx={{ maxWidth: 760, display: 'flex', flexDirection: 'column', gap: 2 }}><Typography variant="h4" component="h1">Settings</Typography><Paper variant="outlined" sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2 }}><Typography variant="h6">Deployment information</Typography><Typography variant="body2" color="text.secondary">These values describe the running deployment. Secrets and mutable infrastructure settings are intentionally never exposed in the browser. Change configuration through the protected server environment or mounted configuration file.</Typography><Divider /><Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>{setting('Application', meta.name)}{setting('Organization', meta.organizationName)}{setting('Version', meta.version)}{setting('Git commit', meta.buildCommit)}{setting('Support email', meta.supportEmail)}{setting('Public URL', meta.publicUrl)}{setting('SSH host', `${meta.sshHost}:${meta.sshPort}`)}{setting('Registration', meta.registrationEnabled ? 'Open' : 'Closed')}{setting('Documentation', meta.documentationUrl)}</Box></Paper></Box>;
}
