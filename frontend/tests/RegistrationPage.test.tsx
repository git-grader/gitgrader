// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { RegistrationPage } from '../src/pages/RegistrationPage';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { expect, test, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as api from '../src/api';
import { MetaProvider } from '../src/components/MetaProvider';

vi.mock('../src/api', async () => {
  const actual = await vi.importActual('../src/api') as any;
  return {
    ...actual,
    api: {
      ...actual.api,
      getAvailability: vi.fn().mockResolvedValue({ 
        open: true, 
        courses: [{ courseKey: 'c1', name: 'Course 1', classes: [{ classKey: 'cl1', name: 'Class 1' }] }] 
      }),
      register: vi.fn(),
      getMeta: vi.fn().mockResolvedValue({ name: 'Test' })
    }
  };
});

const queryClient = new QueryClient();

function renderWithProviders(ui: React.ReactElement) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MetaProvider>
          {ui}
        </MetaProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('RegistrationPage blocks private key', async () => {
  renderWithProviders(<RegistrationPage />);
  
  await screen.findByText(/Register for Test/i);
  
  const keyInput = screen.getByLabelText(/SSH Public Key/i);
  fireEvent.change(keyInput, { target: { value: '-----BEGIN OPENSSH PRIVATE KEY-----' } });
  
  // Bypass HTML5 validation by firing submit on the form directly
  // Note: getByRole('form') requires the form to have an aria-label or title, or just find it by tag
  const form = document.querySelector('form')!;
  fireEvent.submit(form);
  
  await waitFor(() => {
    expect(screen.getByText(/Private key detected/i)).toBeInTheDocument();
  });
  expect(api.api.register).not.toHaveBeenCalled();
});

test('RegistrationPage has no a11y violations', async () => {
  const { container } = renderWithProviders(<RegistrationPage />);
  await screen.findByText(/Register for Test/i);
  const results = await axe(container);
  expect(results).toHaveNoViolations();
});
