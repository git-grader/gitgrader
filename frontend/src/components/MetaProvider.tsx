// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { createContext, useContext, useEffect } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, CircularProgress } from '@mui/material';
import { api } from '../api';
import type { Meta } from '../api';

const MetaContext = createContext<Meta | undefined>(undefined);

/**
 * Loads the deployment's identity before the router renders.
 *
 * This gates the whole application, so the failure path has to be a real one. Holding an
 * undefined value and rendering nothing turned any failed or slow `/api/v1/meta` call —
 * a restart, a proxy hiccup, a 500 — into a blank white page with no spinner, no message
 * and no way back, including for the two routes anyone can reach without signing in.
 */
export function MetaProvider({ children }: { children: ReactNode }) {
  const { data: meta, isPending, isError, refetch } = useQuery({
    queryKey: ['meta'],
    queryFn: () => api.getMeta()
  });

  useEffect(() => {
    if (meta) {
      document.title = meta.name || 'GitGrader';
    }
  }, [meta]);

  if (isPending) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <CircularProgress aria-label="Loading" />
      </Box>
    );
  }

  if (isError) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => void refetch()}>
              Retry
            </Button>
          }
        >
          GitGrader could not be reached. Check that the service is running, then retry.
        </Alert>
      </Box>
    );
  }

  return <MetaContext.Provider value={meta}>{children}</MetaContext.Provider>;
}

export function useMeta() {
  const ctx = useContext(MetaContext);
  if (!ctx) {
    throw new Error('useMeta must be used within MetaProvider');
  }
  return ctx;
}
