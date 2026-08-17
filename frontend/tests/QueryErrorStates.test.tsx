// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { expect, test, vi, beforeEach } from 'vitest';
import { DashboardPage } from '../src/pages/DashboardPage';
import { StudentsPage } from '../src/pages/StudentsPage';
import { ReportPage } from '../src/pages/ReportPage';
import { AdminRuntimesPage } from '../src/pages/AdminRuntimesPage';
import { CoursesPage } from '../src/pages/CoursesPage';
import { SubmissionsPage } from '../src/pages/SubmissionsPage';
import { RegistrationPage } from '../src/pages/RegistrationPage';
import { CourseDetailPage } from '../src/pages/CourseDetailPage';
import { MetaProvider } from '../src/components/MetaProvider';
import { api } from '../src/api';

vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api');
  return {
    ...actual,
    api: {
      getDashboard: vi.fn(),
      getStudents: vi.fn(),
      getCourseReport: vi.fn(),
      getRuntimes: vi.fn(),
      getMe: vi.fn(),
      getCourses: vi.fn(),
      getSubmissions: vi.fn(),
      getAvailability: vi.fn(),
      getCourse: vi.fn(),
      getCourseClasses: vi.fn(),
      getMeta: vi.fn()
    }
  };
});

const mocked = api as unknown as Record<string, ReturnType<typeof vi.fn>>;

beforeEach(() => {
  for (const call of Object.values(mocked)) {
    call.mockReset();
    call.mockRejectedValue(new Error('service unavailable'));
  }
});

// Retries would keep every one of these on the spinner until the test timed out.
function renderPage(page: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/courses/c1/report']}>{page}</MemoryRouter>
    </QueryClientProvider>
  );
}

// Each of these rendered nothing at all when its request failed: no message, no spinner,
// and no way to retry, which is indistinguishable from a page that has finished loading
// and genuinely has nothing on it.
test.each([
  ['dashboard', <DashboardPage key="d" />, 'The dashboard could not be loaded.'],
  ['students', <StudentsPage key="s" />, 'The student list could not be loaded.'],
  ['report', <ReportPage key="r" />, 'The course report could not be loaded.'],
  ['runtimes', <AdminRuntimesPage key="a" />, 'The runtimes could not be loaded.']
])('%s page reports a failed request instead of rendering nothing', async (_name, page, message) => {
  renderPage(page);

  expect(await screen.findByText(message)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
});

test('retrying asks the server again', async () => {
  renderPage(<DashboardPage />);
  await screen.findByText('The dashboard could not be loaded.');
  const calls = mocked['getDashboard']?.mock.calls.length ?? 0;

  screen.getByRole('button', { name: 'Retry' }).click();

  await vi.waitFor(() => {
    expect(mocked['getDashboard']?.mock.calls.length ?? 0).toBeGreaterThan(calls);
  });
});

// These did something worse than render nothing: they stated something untrue. An empty
// list and a failed request are indistinguishable once the data is undefined, so each
// page reported the answer it would have given had the server said there was nothing.
test.each([
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

test('registration does not report itself closed because the check failed', async () => {
  // The harm this prevents: a student inside the registration window is told they
  // missed it, and has no reason to try again.
  mocked['getMeta']?.mockResolvedValue({ name: 'GitGrader' });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MetaProvider>
          <RegistrationPage />
        </MetaProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

  expect(await screen.findByText('Whether registration is open could not be checked. Try again in a moment.'))
    .toBeInTheDocument();
  expect(screen.queryByText('Registration is currently closed.')).not.toBeInTheDocument();
});
