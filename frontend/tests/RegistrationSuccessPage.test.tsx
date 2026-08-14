// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { expect, test, vi, afterEach } from 'vitest';
import { RegistrationSuccessPage } from '../src/pages/RegistrationSuccessPage';

const result = {
  studentId: 'a1',
  studentNumber: '12345',
  fullName: 'Ada Lovelace',
  status: 'SELF_REGISTERED',
  keyFingerprint: 'SHA256:abc',
  repositories: [
    { assignmentKey: 'a-01', assignmentTitle: 'String utilities', cloneUrl: 'ssh://git@localhost:2222/a-01.git' }
  ]
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/register/success', state: { result } }]}>
      <Routes>
        <Route path="/register/success" element={<RegistrationSuccessPage />} />
      </Routes>
    </MemoryRouter>
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
  expect(writeText).toHaveBeenCalledWith('git clone ssh://git@localhost:2222/a-01.git');
});

// There is no clipboard API on an insecure origin, so this is what every student on an
// http:// deployment got: a button that threw where nobody was listening and left the
// page exactly as it was. The clone URL is the one thing they came away with.
test('says so when the browser will not let it copy', async () => {
  vi.stubGlobal('navigator', {});
  renderPage();

  screen.getByRole('button', { name: 'Copy' }).click();

  expect(await screen.findByRole('button', { name: 'Copy it by hand' })).toBeInTheDocument();
  expect(screen.getByText(/ssh:\/\/git@localhost:2222\/a-01\.git/)).toBeInTheDocument();
});
