// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CoursesPage } from '../src/pages/CoursesPage';
import { AssignmentsPage } from '../src/pages/AssignmentsPage';
import { MaterialsPage } from '../src/pages/MaterialsPage';
import { renderWithProviders, server } from './harness';

const COURSE = {
  id: 'c1', courseKey: 'cs101', name: 'Course One', description: null, semester: null,
  startsOn: null, endsOn: null, timezone: 'Europe/Zurich', status: 'ACTIVE',
  registrationOpensAt: null, registrationClosesAt: null, registrationEnabled: true
};

function paged(content: unknown[], number: number) {
  return { content, totalElements: 2, totalPages: 2, size: 1, number };
}

function assignment(id: string, courseId: string, key: string, title: string) {
  return {
    id, courseId, assignmentKey: key, title, description: null, displayOrder: 10,
    status: 'DRAFT', mandatory: true, opensAt: null, dueAt: null, timezone: null,
    maxPoints: 10, testCount: 0, passThreshold: 0, allowLate: false,
    templateVersionId: null, testSuiteVersionId: null, runtimeId: null,
    timeoutSeconds: null, memoryLimitBytes: null, cpuLimit: null, pidLimit: null,
    networkEnabled: false
  };
}


describe('bounded instructor lists', () => {
  it('loads every matching course rather than showing only the first server page', async () => {
    server.use(http.get('/api/v1/courses', ({ request }) => {
      const page = new URL(request.url).searchParams.get('page');
      return HttpResponse.json(page === '1'
        ? paged([{ ...COURSE, id: 'c2', name: 'Course Two' }], 1)
        : paged([COURSE], 0));
    }));

    renderWithProviders(<CoursesPage />);

    expect(await screen.findByRole('link', { name: 'Course Two' })).toBeInTheDocument();
    expect(screen.getByText('Rows per page:')).toBeInTheDocument();
  });

  it('keeps records beyond the first DataGrid page reachable', async () => {
    const courses = Array.from({ length: 101 }, (_value, index) => ({
      ...COURSE,
      id: `c${index + 1}`,
      courseKey: `course-${index + 1}`,
      name: `Course ${index + 1}`
    }));
    server.use(http.get('/api/v1/courses', () => HttpResponse.json({
      content: courses,
      totalElements: courses.length,
      totalPages: 1,
      size: 200,
      number: 0
    })));

    renderWithProviders(<CoursesPage />);

    expect(await screen.findByRole('link', { name: 'Course 1' })).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Go to next page' }));
    expect(await screen.findByRole('link', { name: 'Course 101' })).toBeInTheDocument();
  });

  it('loads every assignment and every course available to its filters', async () => {
    server.use(
      http.get('/api/v1/courses', ({ request }) => {
        const page = new URL(request.url).searchParams.get('page');
        return HttpResponse.json(page === '1'
          ? paged([{ ...COURSE, id: 'c2', name: 'Course Two' }], 1)
          : paged([COURSE], 0));
      }),
      http.get('/api/v1/assignments', ({ request }) => {
        const page = new URL(request.url).searchParams.get('page');
        return HttpResponse.json(page === '1'
          ? paged([assignment('a2', 'c2', 'hw2', 'Homework Two')], 1)
          : paged([assignment('a1', 'c1', 'hw1', 'Homework One')], 0));
      }),
      http.get('/api/v1/templates', () => HttpResponse.json(paged([], 0))),
      http.get('/api/v1/test-suites', () => HttpResponse.json(paged([], 0))),
      http.get('/api/v1/materials/published', () => HttpResponse.json({ templateVersions: [], suiteVersions: [] })),
      http.get('/api/v1/runtimes', () => HttpResponse.json([]))
    );

    renderWithProviders(<AssignmentsPage />);

    expect(await screen.findByRole('link', { name: 'Homework Two' })).toBeInTheDocument();
    expect(screen.getByText('Rows per page:')).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByLabelText('Course Filter'));
    expect(await screen.findByRole('option', { name: 'Course Two' })).toBeInTheDocument();
  });

  it('loads every template and test suite rather than silently truncating materials', async () => {
    server.use(
      http.get('/api/v1/templates', ({ request }) => {
        const page = new URL(request.url).searchParams.get('page');
        return HttpResponse.json(page === '1'
          ? paged([{ id: 't2', templateKey: 'template-two', name: 'Template Two', description: null }], 1)
          : paged([{ id: 't1', templateKey: 'template-one', name: 'Template One', description: null }], 0));
      }),
      http.get('/api/v1/test-suites', ({ request }) => {
        const page = new URL(request.url).searchParams.get('page');
        return HttpResponse.json(page === '1'
          ? paged([{ id: 's2', suiteKey: 'suite-two', name: 'Suite Two', description: null }], 1)
          : paged([{ id: 's1', suiteKey: 'suite-one', name: 'Suite One', description: null }], 0));
      })
    );

    renderWithProviders(<MaterialsPage />);

    expect(await screen.findByText('Template Two')).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('tab', { name: 'Test Suites' }));
    expect(await screen.findByText('Suite Two')).toBeInTheDocument();
  });
});
