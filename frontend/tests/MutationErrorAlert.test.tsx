// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MutationErrorAlert, problemFieldErrors, problemMessage } from '../src/components/MutationErrorAlert';
import { ApiProblem } from '../src/api/client';

/**
 * Eleven dialogs cast their error to a problem document and rendered `detail || title`.
 * Only a response the server sent as `application/problem+json` has either, so a proxy
 * 502 or a dropped connection produced a red box with nothing written in it.
 */
describe('reporting a failed mutation', () => {
  it('prefers the detail the server wrote', () => {
    const problem = new ApiProblem('about:blank', 'Conflict', 409, 'That key is already taken');
    expect(problemMessage(problem)).toBe('That key is already taken');
  });

  it('falls back to the title when there is no detail', () => {
    expect(problemMessage(new ApiProblem('about:blank', 'Conflict', 409))).toBe('Conflict');
  });

  it('says something for a failure that is not a problem document', () => {
    expect(problemMessage(new Error('Failed to fetch'))).toBe('Failed to fetch');
  });

  it('says something even for a failure that carries no message', () => {
    expect(problemMessage(new Error(''))).toBe('The request failed. Try again in a moment.');
    expect(problemMessage('exploded')).toBe('The request failed. Try again in a moment.');
  });

  it('indexes the fields the server named', () => {
    const problem = new ApiProblem('about:blank', 'Validation failed', 400, undefined, undefined, [
      { field: 'courseKey', message: 'already taken' },
      { field: 'name', message: 'must not be blank' }
    ]);
    expect(problemFieldErrors(problem)).toEqual({ courseKey: 'already taken', name: 'must not be blank' });
  });

  it('has no fields to report for an ordinary error', () => {
    expect(problemFieldErrors(new Error('Failed to fetch'))).toEqual({});
  });

  it('renders text rather than an empty banner for a plain error', () => {
    render(<MutationErrorAlert error={new Error('Failed to fetch')} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to fetch');
  });

  it('shows nothing at all when nothing failed', () => {
    render(<MutationErrorAlert error={null} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('moves the reader to the failure', () => {
    render(<MutationErrorAlert error={new Error('Failed to fetch')} />);
    expect(screen.getByRole('alert')).toHaveFocus();
  });
});
