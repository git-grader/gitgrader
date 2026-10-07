// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, expect, it, vi } from 'vitest';
import { RegistrationSuccessPage } from '../src/pages/RegistrationSuccessPage';
import { MetaContext } from '../src/components/MetaProvider';

const result = {
  studentId: 'a1',
  studentUsername: '12345',
  fullName: 'Ada Lovelace',
  status: 'SELF_REGISTERED',
  keyFingerprint: 'SHA256:abc'
};

const meta = {
  name: 'GitGrader',
  organizationName: 'Example',
  supportEmail: 'support@example.org',
  documentationUrl: 'https://example.org/docs',
  publicUrl: 'http://localhost:8080',
  sshHost: 'localhost',
  sshPort: 2222,
  registrationEnabled: true,
  requireInstructorVerification: false,
  version: '0.1.0',
  buildCommit: 'test-commit'
};

const cloneCommand = 'git -c core.sshCommand="ssh -p 2222" clone git@localhost:cs101/<assignment-key>/12345.git';

function renderPage() {
  return render(
    <MetaContext.Provider value={meta}>
      <MemoryRouter initialEntries={[{ pathname: '/register/success', state: { result, courseKey: 'cs101' } }]}>
        <Routes>
          <Route path="/register/success" element={<RegistrationSuccessPage />} />
        </Routes>
      </MemoryRouter>
    </MetaContext.Provider>
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

it('copies the clone command and says so', async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal('navigator', { clipboard: { writeText } });
  renderPage();

  screen.getByRole('button', { name: 'Copy' }).click();

  expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument();
  expect(writeText).toHaveBeenCalledWith(cloneCommand);
});

// There is no clipboard API on an insecure origin, so this is what every student on an
// http:// deployment got: a button that threw where nobody was listening and left the
// page exactly as it was. The clone command is the one thing they came away with, and it
// is built here rather than listed from the response: the repositories are created from
// the registration event and do not exist yet when this page renders.
it('says so when the browser will not let it copy', async () => {
  vi.stubGlobal('navigator', {});
  renderPage();

  screen.getByRole('button', { name: 'Copy' }).click();

  expect(await screen.findByRole('button', { name: 'Copy it by hand' })).toBeInTheDocument();
  expect(screen.getByText(cloneCommand)).toBeInTheDocument();
});

it('keeps the visually hidden status region at one pixel', () => {
  renderPage();

  const status = getComputedStyle(screen.getByRole('status'));
  expect(status.position).toBe('absolute');
  expect(status.width).toBe('1px');
  expect(status.height).toBe('1px');
});

// Port 22 is SSH's default. It is usually the port in use, so the clone command omits it
// rather than printing `:22`; a non-default port is the one worth spelling out.
it('omits the default port 22 from the clone command', () => {
  render(
    <MetaContext.Provider value={{ ...meta, sshPort: 22 }}>
      <MemoryRouter initialEntries={[{ pathname: '/register/success', state: { result, courseKey: 'cs101' } }]}>
        <Routes>
          <Route path="/register/success" element={<RegistrationSuccessPage />} />
        </Routes>
      </MemoryRouter>
    </MetaContext.Provider>
  );

  expect(screen.getByText('git clone git@localhost:cs101/<assignment-key>/12345.git')).toBeInTheDocument();
  expect(screen.queryByText(/localhost:22\//)).not.toBeInTheDocument();
});

// Self-registration does not verify identity, so under the permissive policy the page
// must not promise a later verification that will never happen. The email and student
// username collected at registration are the identity; the clone-and-push invitation is
// unconditional.
it('does not promise verification under the permissive policy', () => {
  renderPage();

  expect(screen.getByText(/clone and push now/)).toBeInTheDocument();
  expect(screen.queryByText(/verify/)).not.toBeInTheDocument();
});

it('says pushes wait for an instructor when verification is required', () => {
  render(
    <MetaContext.Provider value={{ ...meta, requireInstructorVerification: true }}>
      <MemoryRouter initialEntries={[{ pathname: '/register/success', state: { result, courseKey: 'cs101' } }]}>
        <Routes>
          <Route path="/register/success" element={<RegistrationSuccessPage />} />
        </Routes>
      </MemoryRouter>
    </MetaContext.Provider>
  );

  expect(screen.getByText(/pushes are accepted once an instructor verifies your registration/)).toBeInTheDocument();
});
