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
import { api } from '../src/api';

vi.mock('../src/api', async () => {
  const actual = (await vi.importActual('../src/api')) as Record<string, unknown>;
  return {
    ...actual,
    api: {
      getDashboard: vi.fn(),
      getStudents: vi.fn(),
      getCourseReport: vi.fn(),
      getRuntimes: vi.fn(),
      getMe: vi.fn()
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
