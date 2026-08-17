// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useRef } from 'react';
import { Alert } from '@mui/material';
import type { SxProps, Theme } from '@mui/material';
import { ApiProblem } from '../api/client';

/**
 * What a failed mutation says, whatever kind of failure it was.
 *
 * Every dialog used to cast its error to `ApiProblem` and render `detail || title`. Only
 * a response the server sent as `application/problem+json` has either, so a proxy 502, a
 * dropped connection or a response that failed to parse produced an error banner with no
 * text in it at all - a red box saying nothing, eleven times over.
 */
export function problemMessage(error: unknown): string {
  if (error instanceof ApiProblem) {
    return error.detail ?? error.title;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return 'The request failed. Try again in a moment.';
}

/**
 * The per-field messages the server rejected the request with, indexed by field name.
 *
 * Empty for any failure that carries none, which is what lets a form show a general
 * message and per-field messages from the same error without asking which it has.
 */
export function problemFieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiProblem) || !error.errors) return {};
  const fields: Record<string, string> = {};
  for (const entry of error.errors) {
    fields[entry.field] = entry.message;
  }
  return fields;
}

interface MutationErrorAlertProps {
  readonly error: unknown;
  readonly sx?: SxProps<Theme>;
}

/**
 * Shows a failed mutation and moves the reader to it.
 *
 * A submit button that silently stops working is the failure mode this prevents: the
 * alert renders above a long dialog, and someone reading with a screen reader or a
 * magnifier has no reason to look back up unless focus goes there.
 */
export function MutationErrorAlert({ error, sx }: MutationErrorAlertProps) {
  const alertRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (error) {
      alertRef.current?.focus();
    }
  }, [error]);

  if (!error) return null;

  return (
    <Alert ref={alertRef} severity="error" tabIndex={-1} sx={sx}>
      {problemMessage(error)}
    </Alert>
  );
}
