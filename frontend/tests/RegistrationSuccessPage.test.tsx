// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { expect, test, vi, afterEach } from 'vitest';
import { RegistrationSuccessPage } from '../src/pages/RegistrationSuccessPage';
import { MetaContext } from '../src/components/MetaProvider';

const result = {
  studentId: 'a1',
  studentNumber: '12345',
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
  version: '0.1.0'
};

const cloneCommand = 'git clone ssh://git@localhost:2222/cs101/<assignment-key>/12345.git';

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

test('copies the clone command and says so', async () => {
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
test('says so when the browser will not let it copy', async () => {
  vi.stubGlobal('navigator', {});
  renderPage();

  screen.getByRole('button', { name: 'Copy' }).click();

  expect(await screen.findByRole('button', { name: 'Copy it by hand' })).toBeInTheDocument();
  expect(screen.getByText(/ssh:\/\/git@localhost:2222\/cs101/)).toBeInTheDocument();
});
