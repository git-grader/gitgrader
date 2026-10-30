// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, expect, it } from 'vitest';
import { MetaProvider } from '../src/components/MetaProvider';
import { AdminSettingsPage } from '../src/pages/AdminSettingsPage';
import { renderWithProviders, server } from './harness';

function renderPage({ withProvider = true }: { withProvider?: boolean } = {}) {
  const page = <AdminSettingsPage />;
  return renderWithProviders(withProvider ? <MetaProvider>{page}</MetaProvider> : page, {
    route: '/admin/settings'
  });
}

beforeEach(() => {
  server.use(
    http.get('/api/v1/meta', () =>
      HttpResponse.json({
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
      })
    )
  );
});

it('shows the deployed version and git commit', async () => {
  renderPage();

  expect(await screen.findByText('0.1.0-SNAPSHOT')).toBeInTheDocument();
  expect(screen.getByText('Version')).toBeInTheDocument();
  expect(screen.getByText('8f84b13')).toBeInTheDocument();
  expect(screen.getByText('Git commit')).toBeInTheDocument();
});

it('reports a failed settings request instead of rendering half a page', async () => {
  // Rendered without MetaProvider: the page shares the meta query key, so a mounted
  // provider would catch the failure first and this would pin the wrong error branch.
  server.use(http.get('/api/v1/meta', () => HttpResponse.error()));

  renderPage({ withProvider: false });

  expect(await screen.findByText('Deployment settings could not be loaded.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
});
