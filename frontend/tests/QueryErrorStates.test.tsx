// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, expect, it, vi } from 'vitest';
import { DashboardPage } from '../src/pages/DashboardPage';
import { StudentsPage } from '../src/pages/StudentsPage';
import { ReportPage } from '../src/pages/ReportPage';
import { AdminRuntimesPage } from '../src/pages/AdminRuntimesPage';
import { CoursesPage } from '../src/pages/CoursesPage';
import { SubmissionsPage } from '../src/pages/SubmissionsPage';
import { RegistrationPage } from '../src/pages/RegistrationPage';
import { CourseDetailPage } from '../src/pages/CourseDetailPage';
import { MetaProvider } from '../src/components/MetaProvider';
import { meta, renderWithProviders, server } from './harness';

// Every endpoint the pages under test touch fails, so each test pins one failure mode
// without arranging an API surface per page. Registration's meta answer is restored in
// its own test, because the page must know its name to render at all.
beforeEach(() => {
  server.use(
    http.get('/api/v1/meta', () => HttpResponse.error()),
    http.get('/api/v1/*', () => HttpResponse.error()),
    http.post('/api/v1/*', () => HttpResponse.error())
  );
});

// Retries would keep every one of these on the spinner until the test timed out.
function renderPage(page: React.ReactElement) {
  return renderWithProviders(page, { route: '/courses/c1/report' });
}

// Each of these rendered nothing at all when its request failed: no message, no spinner,
// and no way to retry, which is indistinguishable from a page that has finished loading
// and genuinely has nothing on it.
it.each([
  ['dashboard', <DashboardPage key="d" />, 'The dashboard could not be loaded.'],
  ['students', <StudentsPage key="s" />, 'The student list could not be loaded.'],
  ['report', <ReportPage key="r" />, 'The course report could not be loaded.'],
  ['runtimes', <AdminRuntimesPage key="a" />, 'The runtimes could not be loaded.']
])('%s page reports a failed request instead of rendering nothing', async (_name, page, message) => {
  renderPage(page);

  expect(await screen.findByText(message)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
});

it('retrying asks the server again', async () => {
  let dashboardCalls = 0;
  server.use(http.get('/api/v1/dashboard', () => {
    dashboardCalls += 1;
    return HttpResponse.error();
  }));

  renderPage(<DashboardPage />);
  await screen.findByText('The dashboard could not be loaded.');

  screen.getByRole('button', { name: 'Retry' }).click();

  await vi.waitFor(() => {
    expect(dashboardCalls).toBeGreaterThan(1);
  });
});

// These did something worse than render nothing: they stated something untrue. An empty
// list and a failed request are indistinguishable once the data is undefined, so each
// page reported the answer it would have given had the server said there was nothing.
it.each([
  ['courses', <CoursesPage key="c" />, 'The courses could not be loaded.', 'No courses found.'],
  ['submissions', <SubmissionsPage key="s" />, 'The submissions could not be loaded.', null],
  ['course detail', <CourseDetailPage key="cd" />, 'The course could not be loaded.', 'Course not found']
])('%s page does not present a failed request as an answer', async (_name, page, message, lie) => {
  renderPage(page);

  expect(await screen.findByText(message)).toBeInTheDocument();
  if (lie) {
    expect(screen.queryByText(lie)).not.toBeInTheDocument();
  }
});

it('registration does not report itself closed because the check failed', async () => {
  // The harm this prevents: a student inside the registration window is told they
  // missed it, and has no reason to try again.
  server.use(http.get('/api/v1/meta', () => HttpResponse.json(meta())));
  renderWithProviders(
    <MetaProvider>
      <RegistrationPage />
    </MetaProvider>
  );

  expect(await screen.findByText('Whether registration is open could not be checked. Try again in a moment.'))
    .toBeInTheDocument();
  expect(screen.queryByText('Registration is currently closed.')).not.toBeInTheDocument();
});
