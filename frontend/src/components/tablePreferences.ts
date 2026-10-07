// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { createContext, useContext, useMemo, useSyncExternalStore } from 'react';
import { z } from 'zod';

export const PreferenceUserContext = createContext('session');
const modelSchema = z.object({
  density: z.enum(['compact', 'standard', 'comfortable']).default('standard'),
  visibility: z.record(z.string(), z.boolean()).default({}),
  sort: z.array(z.object({ field: z.string().max(100), sort: z.enum(['asc', 'desc']) })).max(1).default([]),
  pageSize: z.union([z.literal(20), z.literal(50), z.literal(100)]).default(20)
});
const schema = z.object({
  model: modelSchema.default({ density: 'standard', visibility: {}, sort: [], pageSize: 20 }),
  views: z.array(z.object({ name: z.string().min(1).max(60), params: z.string().max(4000), model: modelSchema })).max(20).default([])
});
export type TableSettings = z.infer<typeof schema>;
export const defaultTableSettings = (): TableSettings => schema.parse({});
const fallback = new Map<string, string>();
const eventName = 'gitgrader-table-preferences';

export function parseTableSettings(raw: string | null): TableSettings {
  try { return schema.parse(raw ? JSON.parse(raw) as unknown : {}); }
  catch { return defaultTableSettings(); }
}
function read(key: string): string | null {
  try { return fallback.get(key) ?? localStorage.getItem(key); }
  catch { return fallback.get(key) ?? null; }
}
function subscribe(callback: () => void) {
  window.addEventListener('storage', callback);
  window.addEventListener(eventName, callback);
  return () => { window.removeEventListener('storage', callback); window.removeEventListener(eventName, callback); };
}

export function useTableSettings(path: string, defaultPageSize: 20 | 50 | 100 = 20) {
  const user = useContext(PreferenceUserContext);
  const key = `gitgrader:tables:v1:${encodeURIComponent(user)}:${path}`;
  const raw = useSyncExternalStore(subscribe, () => read(key), () => null);
  const settings = useMemo(() => raw ? parseTableSettings(raw) : { ...defaultTableSettings(), model: { ...defaultTableSettings().model, pageSize: defaultPageSize } }, [raw, defaultPageSize]);
  function save(next: TableSettings) {
    const value = JSON.stringify(schema.parse(next));
    try { localStorage.setItem(key, value); fallback.delete(key); }
    catch { fallback.set(key, value); }
    window.dispatchEvent(new Event(eventName));
  }
  return { settings, save };
}

// Only supported UI filter parameters are stored, never response rows or tokens.
const filters = new Set(['courseId', 'studentId', 'assignmentId', 'status', 'q', 'progress', 'enrollment', 'attention', 'assignment', 'eventType', 'actorType']);
export function savedFilterParams(params: URLSearchParams): string {
  return new URLSearchParams([...params].filter(([key]) => filters.has(key))).toString();
}
