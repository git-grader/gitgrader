// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { PageHeader } from '../src/components/PageHeader';

test('renders a semantic page title, description, and actions', () => {
  render(
    <PageHeader
      title="Courses"
      description="Manage course settings and classes."
      actions={<button type="button">New Course</button>}
    />
  );

  expect(screen.getByRole('heading', { level: 1, name: 'Courses' })).toBeInTheDocument();
  expect(screen.getByText('Manage course settings and classes.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'New Course' })).toBeInTheDocument();
});