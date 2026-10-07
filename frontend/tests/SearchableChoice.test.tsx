// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchableChoice } from '../src/components/SearchableChoice';
import { renderWithProviders } from './harness';

function Picker() {
  const [value, setValue] = useState('');
  return <><SearchableChoice label="Runtime" value={value} onChange={setValue} options={[
    { id: 'node', label: 'Node.js 24' }, { id: 'java', label: 'Java 25' }
  ]} /><output aria-label="Selected runtime">{value || 'None'}</output></>;
}

it('searches options, selects by id and can clear the selection', async () => {
  renderWithProviders(<Picker />);
  const user = userEvent.setup();
  await user.type(screen.getByRole('combobox', { name: 'Runtime' }), 'Java');
  expect(screen.queryByRole('option', { name: 'Node.js 24' })).not.toBeInTheDocument();
  await user.click(screen.getByRole('option', { name: 'Java 25' }));
  expect(screen.getByLabelText('Selected runtime')).toHaveTextContent('java');
  await user.click(screen.getByRole('button', { name: 'Clear' }));
  expect(screen.getByLabelText('Selected runtime')).toHaveTextContent('None');
});

it('retains a saved choice missing from the current published catalogue', () => {
  renderWithProviders(<SearchableChoice label="Template Version" value="retired" options={[]} onChange={() => {}} disabled />);
  expect(screen.getByRole('combobox', { name: 'Template Version' })).toHaveValue('Saved selection (retired)');
});
