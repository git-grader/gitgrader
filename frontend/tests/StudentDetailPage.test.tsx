// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { StudentDetailPage } from '../src/pages/StudentDetailPage';
import { renderWithProviders, server } from './harness';

beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

const STUDENT = {
  id: 's1',
  studentUsername: 'alice',
  firstName: 'Alice',
  lastName: 'Lee',
  email: 'alice@example.org',
  status: 'ACTIVE'
};

function renderDetail(routes = [<Route key="d" path="/students/:id" element={<StudentDetailPage />} />]) {
  renderWithProviders(<Routes>{routes}</Routes>, { route: '/students/s1' });
}

function stubStudent() {
  server.use(http.get('/api/v1/students/s1', () => HttpResponse.json({ student: STUDENT, sshKeys: [] })));
}

test('edits the loaded student', async () => {
  stubStudent();
  renderDetail();

  expect(await screen.findByRole('heading', { name: 'Edit Student' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: 'Student ID / Username' })).toHaveValue('alice');
  expect(screen.getByRole('textbox', { name: 'First Name' })).toHaveValue('Alice');
  expect(screen.getByRole('textbox', { name: 'Last Name' })).toHaveValue('Lee');
  expect(screen.getByRole('textbox', { name: 'Email' })).toHaveValue('alice@example.org');
});

test('saves the changes and returns to the list', async () => {
  stubStudent();
  let body: unknown;
  server.use(http.put('/api/v1/students/s1', async ({ request }) => {
    body = await request.json();
    return HttpResponse.json({ ...STUDENT, firstName: 'Alicia' });
  }));
  renderDetail([<Route key="l" path="/students" element={<div>Students list</div>} />,
    <Route key="d" path="/students/:id" element={<StudentDetailPage />} />]);

  await screen.findByRole('heading', { name: 'Edit Student' });
  fireEvent.change(screen.getByRole('textbox', { name: 'First Name' }), { target: { value: 'Alicia' } });
  await userEvent.click(screen.getByRole('button', { name: 'Save' }));

  await waitFor(() => { expect(body).toMatchObject({ firstName: 'Alicia', studentUsername: 'alice', email: 'alice@example.org' }); });
  expect(await screen.findByText('Students list')).toBeInTheDocument();
});

test('shows the field the server rejected the update on', async () => {
  stubStudent();
  server.use(http.put('/api/v1/students/s1', () => new HttpResponse(
    JSON.stringify({
      type: 'about:blank',
      title: 'Unprocessable Entity',
      status: 422,
      detail: 'The update was rejected.',
      errors: [{ field: 'studentUsername', message: 'That student username is already registered' }]
    }),
    { status: 422, headers: { 'content-type': 'application/problem+json' } }
  )));
  renderDetail();

  await screen.findByRole('heading', { name: 'Edit Student' });
  await userEvent.click(screen.getByRole('button', { name: 'Save' }));

  expect(await screen.findByText('The update was rejected.')).toBeInTheDocument();
  expect(screen.getByText('That student username is already registered')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
});