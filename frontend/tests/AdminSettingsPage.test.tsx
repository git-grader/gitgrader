// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { expect, test, vi, beforeEach } from 'vitest';
import { AdminSettingsPage } from '../src/pages/AdminSettingsPage';
import { api } from '../src/api';

vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api');
  return {
    ...actual,
    api: {
      getMeta: vi.fn()
    }
  };
});

const getMeta = api.getMeta as ReturnType<typeof vi.fn>;

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin/settings']}>
        <AdminSettingsPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  getMeta.mockReset();
});

test('shows the deployed version and git commit', async () => {
  getMeta.mockResolvedValue({
    name: 'GitGrader',
    organizationName: 'Example Organization',
    supportEmail: 'support@example.org',
    documentationUrl: 'https://docs.example.org',
    publicUrl: 'https://grader.example.org',
    sshHost: 'ssh.example.org',
    sshPort: 2222,
    registrationEnabled: true,
    requireInstructorVerification: false,
    version: '0.1.0-SNAPSHOT',
    buildCommit: '8f84b13'
  });

  renderPage();

  expect(await screen.findByText('0.1.0-SNAPSHOT')).toBeInTheDocument();
  expect(screen.getByText('Version')).toBeInTheDocument();
  expect(screen.getByText('8f84b13')).toBeInTheDocument();
  expect(screen.getByText('Git commit')).toBeInTheDocument();
});

test('reports a failed meta request instead of rendering half a page', async () => {
  getMeta.mockRejectedValue(new Error('service unavailable'));

  renderPage();

  expect(await screen.findByText('Deployment settings could not be loaded.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
});