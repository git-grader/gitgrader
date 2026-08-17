// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { RegistrationPage } from '../src/pages/RegistrationPage';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import { expectNoAxeViolations } from './harness';
import * as api from '../src/api';
import { ApiProblem } from '../src/api/client';
import { MetaProvider } from '../src/components/MetaProvider';

vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api');
  return {
    ...actual,
    api: {
      ...actual.api,
      getAvailability: vi.fn(),
      register: vi.fn(),
      getMeta: vi.fn()
    }
  };
});

const availability = api.api.getAvailability as ReturnType<typeof vi.fn>;
const register = api.api.register as ReturnType<typeof vi.fn>;
const getMeta = api.api.getMeta as ReturnType<typeof vi.fn>;

beforeEach(() => {
  getMeta.mockResolvedValue({ name: 'Test' });
  availability.mockResolvedValue({
    open: true,
    courses: [{ courseKey: 'c1', name: 'Course 1', classes: [{ classKey: 'cl1', name: 'Class 1' }] }]
  });
  register.mockReset();
});

// A fresh client per test: a shared one keeps the first availability answer for every
// later test, which quietly hides whatever the later test set up.
function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
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

async function fillRegistration(courseName = 'Course 1', className: string | null = 'Class 1') {
  const user = userEvent.setup();
  // Set rather than typed: every keystroke re-renders the whole form, which under
  // coverage instrumentation costs more than the test is measuring.
  const setValue = (field: HTMLElement, value: string) =>
    { fireEvent.change(field, { target: { value } }); };

  setValue(screen.getByRole('textbox', { name: 'First Name' }), 'Ada');
  setValue(screen.getByRole('textbox', { name: 'Last Name' }), 'Lovelace');
  setValue(screen.getByRole('textbox', { name: 'Student Number' }), '001');
  setValue(screen.getByLabelText(/Email/), 'ada@example.org');

  await user.click(screen.getByRole('combobox', { name: 'Course' }));
  await user.click(await screen.findByRole('option', { name: courseName }));

  if (className) {
    await user.click(await screen.findByRole('combobox', { name: 'Class' }));
    await user.click(await screen.findByRole('option', { name: className }));
  }

  setValue(screen.getByRole('textbox', { name: 'SSH Public Key' }), 'ssh-ed25519 AAAA ada@example.org');

  const form = document.querySelector('form');
  if (!form) throw new Error('the registration form did not render');
  fireEvent.submit(form);
}

test('RegistrationPage blocks private key', async () => {
  renderWithProviders(<RegistrationPage />);

  await screen.findByText(/Register for Test/i);

  const keyInput = screen.getByLabelText(/SSH Public Key/i);
  fireEvent.change(keyInput, { target: { value: '-----BEGIN OPENSSH PRIVATE KEY-----' } });

  // Bypass HTML5 validation by firing submit on the form directly: the browser would
  // otherwise stop at the first empty required field and never reach the key check.
  const form = document.querySelector('form');
  if (!form) throw new Error('the registration form did not render');
  fireEvent.submit(form);

  await waitFor(() => {
    expect(screen.getByText(/Private key detected/i)).toBeInTheDocument();
  });
  expect(register).not.toHaveBeenCalled();
});

test('RegistrationPage has no a11y violations', async () => {
  const { container } = renderWithProviders(<RegistrationPage />);
  await screen.findByText(/Register for Test/i);
  await expectNoAxeViolations(container);
});

/**
 * The server explains a refusal in `detail` and names the offending fields in `errors`.
 * The page showed `error.message`, which for a problem document is its title, so
 * "that student number is already registered" reached the student as "Bad Request".
 */
test('RegistrationPage shows what the server actually objected to', async () => {
  register.mockRejectedValue(
    new ApiProblem('about:blank', 'Bad Request', 400, 'That student number is already registered', undefined, [
      { field: 'studentNumber', message: 'already registered' }
    ])
  );

  renderWithProviders(<RegistrationPage />);
  await screen.findByText(/Register for Test/i);
  await fillRegistration();

  expect(await screen.findByText('That student number is already registered')).toBeInTheDocument();
  expect(screen.getByText('already registered')).toBeInTheDocument();
  expect(screen.queryByText('Bad Request')).not.toBeInTheDocument();
});

test('RegistrationPage says something for a failure that is not a problem document', async () => {
  register.mockRejectedValue(new Error('Failed to fetch'));

  renderWithProviders(<RegistrationPage />);
  await screen.findByText(/Register for Test/i);
  await fillRegistration();

  expect(await screen.findByText('Failed to fetch')).toBeInTheDocument();
});

/**
 * A course need not be divided into classes, and the server accepts a registration
 * without one. The form required a class from a dropdown that had nothing in it, so such
 * a course could not be registered for and nothing on screen said why.
 */
test('RegistrationPage registers for a course that has no classes', async () => {
  availability.mockResolvedValue({
    open: true,
    courses: [{ courseKey: 'solo', name: 'Class-less Course', classes: [] }]
  });
  register.mockResolvedValue({
    studentId: 's1', studentNumber: '001', fullName: 'A Student', status: 'SELF_REGISTERED',
    keyFingerprint: 'SHA256:abc', repositories: []
  });

  renderWithProviders(<RegistrationPage />);
  await screen.findByText(/Register for Test/i);
  await fillRegistration('Class-less Course', null);

  await waitFor(() => { expect(register).toHaveBeenCalled(); });
  expect(screen.getByText(/not divided into classes/)).toBeInTheDocument();
});
