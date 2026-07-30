// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api';
import type { Meta } from '../api';

const MetaContext = createContext<Meta | undefined>(undefined);

export function MetaProvider({ children }: { children: ReactNode }) {
  const [meta, setMeta] = useState<Meta | undefined>(undefined);

  useEffect(() => {
    api.getMeta().then(data => {
      setMeta(data);
      document.title = data.name || 'GitGrader';
    }).catch((err: unknown) => {
      console.error('Failed to load meta', err);
    });
  }, []);

  if (!meta) return null; // or a loading spinner

  return (
    <MetaContext.Provider value={meta}>
      {children}
    </MetaContext.Provider>
  );
}

export function useMeta() {
  const ctx = useContext(MetaContext);
  if (!ctx) {
    throw new Error('useMeta must be used within MetaProvider');
  }
  return ctx;
}
