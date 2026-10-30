// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, expect, it } from 'vitest';
import userEvent from '@testing-library/user-event';
import { RegistrationPage } from '../src/pages/RegistrationPage';
import { MetaProvider } from '../src/components/MetaProvider';
import { expectNoAxeViolations, meta, renderWithProviders, server } from './harness';

const AVAILABILITY = {
  open: true,
  courses: [{ courseKey: 'c1', name: 'Course 1', classes: [{ classKey: 'cl1', name: 'Class 1' }] }]
};

const REGISTRATION_RESULT = {
  studentId: 's1', studentUsername: '001', fullName: 'A Student', status: 'SELF_REGISTERED',
  keyFingerprint: 'SHA256:abc', repositories: []
};

// The test's own handler set: registration availability, the registration itself and the
// meta the page's provider reads. A test that needs a different answer overrides one of
// these rather than re-arming the API surface.
beforeEach(() => {
  server.use(
    http.get('/api/v1/meta', () => HttpResponse.json(meta())),
    http.get('/api/v1/registration/availability', () => HttpResponse.json(AVAILABILITY)),
    http.post('/api/v1/registration', () => HttpResponse.json(REGISTRATION_RESULT))
  );
});

function renderPage() {
  return renderWithProviders(
    <MetaProvider>
      <RegistrationPage />
    </MetaProvider>
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
  setValue(screen.getByRole('textbox', { name: 'Student ID / Username' }), '001');
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

it('blocks a private key', async () => {
  // The guard has two halves: the UI must warn, and nothing may reach the server. The
  // handler counts, so the second assertion is that the count stays at zero.
  let registrations = 0;
  server.use(http.post('/api/v1/registration', () => {
    registrations += 1;
    return HttpResponse.json(REGISTRATION_RESULT);
  }));

  renderPage();

  await screen.findByText(/Register for Test Instance/i);

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
  expect(registrations).toBe(0);
});

it('has no a11y violations', async () => {
  const { container } = renderPage();
  await screen.findByText(/Register for Test Instance/i);
  await expectNoAxeViolations(container);
});

/**
 * The server explains a refusal in `detail` and names the offending fields in `errors`.
 * The page showed `error.message`, which for a problem document is its title, so
 * "that student username is already registered" reached the student as "Bad Request".
 */
it('shows what the server actually objected to', async () => {
  server.use(http.post('/api/v1/registration', () =>
    new HttpResponse(JSON.stringify({
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'That student username is already registered',
      errors: [{ field: 'studentUsername', message: 'already registered' }]
    }), { status: 400, headers: { 'content-type': 'application/problem+json' } })));

  renderPage();
  await screen.findByText(/Register for Test Instance/i);
  await fillRegistration();

  expect(await screen.findByText('That student username is already registered')).toBeInTheDocument();
  expect(screen.getByText('already registered')).toBeInTheDocument();
  expect(screen.queryByText('Bad Request')).not.toBeInTheDocument();
});

it('says something for a failure that is not a problem document', async () => {
  // A proxy answers with its own HTML or plain text, which carries no `detail` to show.
  server.use(http.post('/api/v1/registration', () =>
    new HttpResponse('Bad gateway', { status: 502, headers: { 'content-type': 'text/plain' } })));

  renderPage();
  await screen.findByText(/Register for Test Instance/i);
  await fillRegistration();

  expect(await screen.findByText('API error: 502 Bad Gateway')).toBeInTheDocument();
});

/**
 * A course need not be divided into classes, and the server accepts a registration
 * without one. The form required a class from a dropdown that had nothing in it, so such
 * a course could not be registered for and nothing on screen said why.
 */
it('registers for a course that has no classes', async () => {
  let registered = false;
  server.use(
    http.get('/api/v1/registration/availability', () => HttpResponse.json({
      open: true,
      courses: [{ courseKey: 'solo', name: 'Class-less Course', classes: [] }]
    })),
    http.post('/api/v1/registration', () => {
      registered = true;
      return HttpResponse.json(REGISTRATION_RESULT);
    })
  );

  renderPage();
  await screen.findByText(/Register for Test Instance/i);
  await fillRegistration('Class-less Course', null);

  await waitFor(() => { expect(registered).toBe(true); });
  expect(screen.getByText(/not divided into classes/)).toBeInTheDocument();
});
