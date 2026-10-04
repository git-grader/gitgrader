// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router';
import { AssignmentSchema } from '../src/api';
import { BulkVerification } from '../src/components/BulkVerification';
import { DuplicateAssignment } from '../src/components/DuplicateAssignment';
import { AssignmentReadiness, assignmentReadiness } from '../src/components/AssignmentReadiness';
import { InstructorDataGrid } from '../src/components/InstructorDataGrid';
import { PreferenceUserContext, parseTableSettings, savedFilterParams } from '../src/components/tablePreferences';
import { DeadlinesPage } from '../src/pages/DeadlinesPage';
import { deadlineCalendar, deadlineLabel, foldCalendarLine } from '../src/features/calendar';
import { classFollowUps } from '../src/features/followUps';
import { renderWithProviders, server, page } from './harness';

const assignment = AssignmentSchema.parse({
  id: 'a1', courseId: 'c1', assignmentKey: 'strings', title: 'String utilities', displayOrder: 1,
  status: 'OPEN', mandatory: true, maxPoints: 10, testCount: 2, passThreshold: 50, allowLate: false,
  opensAt: '2030-01-01T12:00:00Z', dueAt: '2030-01-02T12:00:00Z', timezone: 'Europe/Zurich',
  templateVersionId: 'tv1', testSuiteVersionId: 'sv1', runtimeId: 'rt1', networkEnabled: false
});
const course = { id: 'c1', courseKey: 'course', name: 'Course One', timezone: 'Europe/Zurich', status: 'ACTIVE', registrationEnabled: true };
const students = ['ada', 'grace'].map((name, i) => ({ id: `st${i}`, studentUsername: name, firstName: name, lastName: 'Student', email: `${name}@example.org`, status: 'SELF_REGISTERED' }));
const materials = { isLoading: false, isError: false, publishedTemplateVersions: [{ id: 'tv1', label: 'Template' }], publishedSuiteVersions: [{ id: 'sv1', label: 'Suite' }], runtimes: [{ id: 'rt1', runtimeKey: 'node', displayName: 'Node', image: 'node', tag: '24', imageDigest: 'digest', testCommand: 'test', reportFormat: 'TAP', enabled: true, createdAt: '', updatedAt: '' }] };

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
beforeEach(() => { localStorage.clear(); });
afterEach(() => { server.resetHandlers(); vi.restoreAllMocks(); });
afterAll(() => server.close());

it('rejects corrupt preferences and saves only known filter parameters', () => {
  expect(parseTableSettings('{broken').views).toEqual([]);
  expect(parseTableSettings('{"model":{"density":"unknown"}}').model.density).toBe('standard');
  expect(savedFilterParams(new URLSearchParams('status=FAILED&token=secret&rows=private&q=Ada'))).toBe('status=FAILED&q=Ada');
});

function Location() { return <output aria-label="Location">{useLocation().search}</output>; }
function Table({ username = 'teacher' }: { username?: string }) {
  return <PreferenceUserContext.Provider value={username}><Location /><InstructorDataGrid rows={[{ id: 'r1', name: 'Row', score: 10 }]} columns={[{ field: 'name', headerName: 'Name' }, { field: 'score', headerName: 'Score' }]} /></PreferenceUserContext.Provider>;
}
it('persists table preferences and restores named filters without saving rows', async () => {
  const user = userEvent.setup();
  const rendered = renderWithProviders(<Table />, { route: '/submissions?status=FAILED' });
  await user.click(screen.getByRole('button', { name: 'Table options' }));
  await user.click(screen.getByRole('combobox', { name: 'Density' }));
  await user.click(screen.getByRole('option', { name: 'compact' }));
  await user.click(screen.getByRole('checkbox', { name: 'Score' }));
  await user.click(screen.getByRole('button', { name: 'Done' }));
  expect(screen.queryByRole('columnheader', { name: 'Score' })).not.toBeInTheDocument();
  await user.click(await screen.findByRole('button', { name: 'Save view' }));
  await user.type(screen.getByRole('textbox', { name: 'View name' }), 'Failures');
  await user.click(screen.getByRole('button', { name: /^Save$/ }));
  const stored = localStorage.getItem('gitgrader:tables:v1:teacher:/submissions') ?? '';
  expect(stored).toContain('Failures'); expect(stored).not.toContain('r1'); expect(stored).not.toContain('Row');
  rendered.unmount();
  renderWithProviders(<Table />, { route: '/submissions' });
  expect(screen.queryByRole('columnheader', { name: 'Score' })).not.toBeInTheDocument();
  await user.click(screen.getByRole('combobox', { name: 'Saved views' }));
  await user.click(screen.getByRole('option', { name: 'Failures' }));
  expect(screen.getByLabelText('Location')).toHaveTextContent('?status=FAILED');
});
it('isolates table preferences by instructor', async () => {
  localStorage.setItem('gitgrader:tables:v1:first:/submissions', JSON.stringify({ model: { density: 'compact' }, views: [{ name: 'Private view', params: 'status=FAILED', model: {} }] }));
  renderWithProviders(<Table username="second" />, { route: '/submissions' });
  await userEvent.setup().click(screen.getByRole('combobox', { name: 'Saved views' }));
  expect(screen.queryByRole('option', { name: 'Private view' })).not.toBeInTheDocument();
});

it('readiness checks catalogue membership, enabled runtimes and invalid grading settings', () => {
  expect(assignmentReadiness(assignment, materials).every(check => check.ready)).toBe(true);
  const runtime = materials.runtimes[0];
  if (!runtime) throw new Error('Runtime fixture is missing');
  const invalid = { ...assignment, templateVersionId: 'retired', dueAt: assignment.opensAt, testCount: 0, passThreshold: 101 };
  expect(assignmentReadiness(invalid, { ...materials, runtimes: [{ ...runtime, enabled: false }] }).filter(check => !check.ready)).toHaveLength(4);
});
it('renders readiness links into the saved configuration', () => {
  renderWithProviders(<AssignmentReadiness assignment={{ ...assignment, status: 'DRAFT', runtimeId: null }} materials={materials} />);
  expect(screen.getByRole('heading', { name: 'Publication readiness' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Review configuration' })).toHaveAttribute('href', '#assignment-configuration');
});

it('duplicates only after review and sends a draft without response-only fields or old dates', async () => {
  const sent: unknown[] = [];
  server.use(http.get('/api/v1/courses', () => HttpResponse.json(page([course]))), http.post('/api/v1/assignments', async ({ request }) => {
    sent.push(await request.json()); return HttpResponse.json({ ...assignment, id: 'copy', status: 'DRAFT' });
  }));
  const user = userEvent.setup();
  renderWithProviders(<Routes><Route path="/assignments/a1" element={<DuplicateAssignment assignment={assignment} />} /><Route path="/assignments/copy" element={<p>Created draft</p>} /></Routes>, { route: '/assignments/a1' });
  await user.click(screen.getByRole('button', { name: 'Duplicate as draft' }));
  expect(sent).toEqual([]);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Create draft' })).toBeEnabled());
  await user.click(screen.getByRole('button', { name: 'Create draft' }));
  await screen.findByText('Created draft');
  expect(sent).toHaveLength(1);
  expect(sent[0]).toMatchObject({ status: 'DRAFT', assignmentKey: 'strings-copy', templateVersionId: 'tv1', opensAt: null, dueAt: null });
  expect(sent[0]).not.toHaveProperty('id');
});
it('rejects reuse of the original assignment key', async () => {
  server.use(http.get('/api/v1/courses', () => HttpResponse.json(page([course]))));
  const user = userEvent.setup(); renderWithProviders(<DuplicateAssignment assignment={assignment} />);
  await user.click(screen.getByRole('button', { name: 'Duplicate as draft' }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Create draft' })).toBeEnabled());
  await user.clear(screen.getByRole('textbox', { name: 'New assignment key' })); await user.type(screen.getByRole('textbox', { name: 'New assignment key' }), 'strings');
  await user.click(screen.getByRole('button', { name: 'Create draft' })); expect(screen.getByText('Use a new assignment key.')).toBeInTheDocument();
});

it('bulk verification cancels without writes, reports partial results, and retries only failures', async () => {
  let failGrace = true; const writes: string[] = [];
  server.use(http.get('/api/v1/students/:id', ({ params }) => HttpResponse.json({ student: students.find(student => student.id === params['id']), sshKeys: [] })),
    http.patch('/api/v1/students/:id/status', ({ params }) => {
      const id = String(params['id']); writes.push(id);
      if (id === 'st1' && failGrace) return HttpResponse.json({ detail: 'Try again' }, { status: 503 });
      return HttpResponse.json({ id, studentUsername: id, fullName: id, email: 'test@example.org', status: 'VERIFIED_BY_INSTRUCTOR', registeredAt: '2030-01-01T00:00:00Z' });
    }));
  const user = userEvent.setup(); renderWithProviders(<BulkVerification students={students} disabled={false} onFinished={() => {}} onBusyChange={() => {}} />);
  await user.click(await screen.findByRole('button', { name: 'Verify selected (2)' })); await user.click(screen.getByRole('button', { name: 'Cancel' })); expect(writes).toEqual([]);
  await user.click(await screen.findByRole('button', { name: 'Verify selected (2)' })); await user.click(screen.getByRole('button', { name: 'Confirm verification' }));
  await screen.findByText('1 verified · 1 failed · 0 skipped');
  failGrace = false; await user.click(screen.getByRole('button', { name: 'Retry failed only' })); await screen.findByText('2 verified · 0 failed · 0 skipped');
  expect(writes).toEqual(['st0', 'st1', 'st1']);
});
it('bulk verification skips registrations whose status changed', async () => {
  server.use(http.get('/api/v1/students/st0', () => HttpResponse.json({ student: { ...students[0], status: 'SUSPENDED' }, sshKeys: [] })));
  const user = userEvent.setup(); renderWithProviders(<BulkVerification students={students.slice(0, 1)} disabled={false} onFinished={() => {}} onBusyChange={() => {}} />);
  await user.click(screen.getByRole('button', { name: 'Verify selected (1)' })); await user.click(screen.getByRole('button', { name: 'Confirm verification' }));
  await screen.findByText('0 verified · 0 failed · 1 skipped');
});

it('calendar preserves UTC instants, escapes injection, and folds Unicode by octets', () => {
  const calendar = deadlineCalendar([{ ...assignment, title: 'Test, punctuation; slash\\\nBEGIN:VEVENT ' + '🌍'.repeat(40) }, { ...assignment, id: 'invalid', dueAt: 'invalid' }], [course], 'https://grader.example.org', new Date('2030-01-01T00:00:00Z'));
  expect(calendar).toContain('DTSTART:20300102T120000Z\r\n');
  expect(calendar.match(/BEGIN:VEVENT/g)).toHaveLength(2); // One real event plus escaped text inside its summary.
  expect(calendar).not.toContain('\r\nBEGIN:VEVENT ');
  expect(calendar).toContain('UID:a1@grader.example.org');
  expect(calendar).toContain('URL:https://grader.example.org/assignments/a1');
  expect(calendar.split('\r\n').every(line => new TextEncoder().encode(line).length <= 75)).toBe(true);
  expect(foldCalendarLine('🌍'.repeat(40)).replace(/\r\n /g, '')).toBe('🌍'.repeat(40));
  expect(deadlineLabel(assignment.dueAt ?? '', 'Europe/Zurich')).toContain('Europe/Zurich');
  expect(deadlineLabel(assignment.dueAt ?? '', 'not-a-zone')).toContain('UTC');
  expect(deadlineLabel('invalid', 'UTC')).toBe('Invalid deadline');
});
it('deadline view groups published upcoming assignments and offers drafts only in the all view', async () => {
  server.use(http.get('/api/v1/courses', () => HttpResponse.json(page([course]))), http.get('/api/v1/assignments', () => HttpResponse.json(page([
    assignment, { ...assignment, id: 'draft', title: 'Draft assignment', status: 'DRAFT' }, { ...assignment, id: 'past', title: 'Old assignment', dueAt: '2020-01-01T12:00:00Z' }
  ]))));
  const user = userEvent.setup(); renderWithProviders(<DeadlinesPage />);
  await screen.findByRole('link', { name: 'String utilities' }); expect(screen.queryByRole('link', { name: 'Draft assignment' })).not.toBeInTheDocument();
  await user.click(screen.getByRole('combobox', { name: 'Deadlines to show' })); await user.click(screen.getByRole('option', { name: 'All dated assignments, including drafts' }));
  expect(screen.getByRole('link', { name: 'Draft assignment' })).toBeInTheDocument(); expect(screen.getByRole('link', { name: 'Old assignment' })).toBeInTheDocument();
  expect(within(screen.getByRole('region', { name: 'Course One deadlines' })).getAllByRole('article')).toHaveLength(3);
});

it('class follow-up counts active students once per category and excludes withdrawn enrollments', () => {
  const student = { studentId: 's', studentUsername: 's', fullName: 'Student', status: 'SELF_REGISTERED', enrollmentStatus: 'ACTIVE', fullyCompleted: 0, partiallyCompleted: 0, notStarted: 2, completionRate: 0, pointsEarned: 0, pointsRate: 0, totalPoints: 10, submissionCount: 2, assignments: { a: { bestPercent: 0, bestPoints: 0, latestSubmission: { ...assignment, id: 's1', commitSha: 'sha', gitRef: 'main', commitMessage: '', receivedAt: '', status: 'FAILED', late: false }, latestGrading: null }, b: { bestPercent: 0, bestPoints: 0, latestSubmission: { id: 's2', commitSha: 'sha', gitRef: 'main', commitMessage: '', receivedAt: '', status: 'FAILED', late: false }, latestGrading: null } } };
  expect(classFollowUps({ courseId: 'c1', classId: 'cl1', classKey: 'cl', className: 'Class', totalMandatoryAssignments: 2, totalPointsAvailable: 10, assignments: [], students: [student, { ...student, studentId: 'withdrawn', enrollmentStatus: 'WITHDRAWN' }] })).toEqual({ missing: 1, failed: 1, infrastructure: 0 });
});

it('duplicates dates using the selected course timezone when the source has no override', async () => {
  const sent: unknown[] = [];
  server.use(http.get('/api/v1/courses', () => HttpResponse.json(page([{ ...course, timezone: 'America/New_York' }]))),
    http.post('/api/v1/assignments', async ({ request }) => { sent.push(await request.json()); return HttpResponse.json({ ...assignment, id: 'copy', status: 'DRAFT' }); }));
  const user = userEvent.setup(); renderWithProviders(<DuplicateAssignment assignment={{ ...assignment, timezone: null }} />);
  await user.click(screen.getByRole('button', { name: 'Duplicate as draft' }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Create draft' })).toBeEnabled());
  expect(screen.getByText(/Dates use America\/New_York/)).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Use original dates' }));
  await user.click(screen.getByRole('button', { name: 'Create draft' }));
  await waitFor(() => expect(sent).toHaveLength(1));
  expect(sent[0]).toMatchObject({ timezone: 'America/New_York', opensAt: new Date(assignment.opensAt ?? '').toISOString(), dueAt: new Date(assignment.dueAt ?? '').toISOString(), status: 'DRAFT' });
});

it('table preferences remain usable when persistent storage is blocked', async () => {
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Storage blocked'); });
  const user = userEvent.setup(); renderWithProviders(<Table username="private-mode" />, { route: '/submissions' });
  await user.click(screen.getByRole('button', { name: 'Table options' }));
  await user.click(screen.getByRole('combobox', { name: 'Density' })); await user.click(screen.getByRole('option', { name: 'compact' }));
  expect(screen.getByRole('combobox', { name: 'Density' })).toHaveTextContent('compact');
  await user.click(screen.getByRole('button', { name: 'Reset table preferences' }));
  expect(screen.getByRole('combobox', { name: 'Density' })).toHaveTextContent('standard');
});
