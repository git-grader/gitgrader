// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { createServer } from 'vite';

// Real layout assertions need a browser: jsdom gives collapsed and visible grids
// the same zero-sized rectangles. All responses here are synthetic and offline.
const course = {
  id: 'c1', courseKey: 'course', name: 'Browser test course', timezone: 'Europe/Zurich',
  status: 'ACTIVE', registrationEnabled: true
};
const assignment = {
  id: 'a1', courseId: 'c1', assignmentKey: 'strings', title: 'String utilities',
  displayOrder: 1, status: 'DRAFT', mandatory: true, maxPoints: 10,
  testCount: 4, passThreshold: 50, allowLate: false, networkEnabled: false
};
const student = {
  studentId: 'st1', studentUsername: 'student', fullName: 'Browser Test Student',
  fullyCompleted: 0, partiallyCompleted: 1, notStarted: 1, completionRate: 0,
  pointsEarned: 5, pointsRate: 0.5, totalPoints: 10, submissionCount: 1,
  lastActivityAt: null, assignments: { strings: { percent: 50, points: 5 } }
};
const report = { courseId: 'c1', totalMandatoryAssignments: 2, totalPointsAvailable: 10, students: [student] };
const submission = {
  id: 's1', repositoryId: 'r1', studentId: 'st1', studentUsername: 'student',
  courseId: 'c1', assignmentId: 'a1', commitSha: 'a'.repeat(40), shortCommitSha: 'aaaaaaa',
  gitRef: 'refs/heads/main', commitMessage: 'Implement the assignment',
  receivedAt: '2026-09-20T12:00:00Z', signatureStatus: 'VERIFIED', status: 'FAILED', late: false
};
const classReport = {
  ...report, classId: 'cl1', classKey: 'class', className: 'Browser test class',
  assignments: [{
    assignmentId: 'a1', assignmentKey: 'strings', title: 'String utilities', mandatory: true,
    maxPoints: 10, testCount: 4, submissionCount: 1, passedCount: 0, failedCount: 1,
    infrastructureErrorCount: 0, notStartedCount: 1, averagePercent: 50
  }],
  students: [{
    ...student, status: 'VERIFIED_BY_INSTRUCTOR', enrollmentStatus: 'ACTIVE',
    assignments: { strings: { bestPercent: 50, bestPoints: 5, latestSubmission: submission, latestGrading: null } }
  }]
};
const meta = {
  name: 'GitGrader', organizationName: 'Test Organization', supportEmail: 'test@example.org',
  documentationUrl: 'https://example.org/docs', publicUrl: 'https://example.org',
  sshHost: 'example.org', sshPort: 2222, registrationEnabled: true,
  requireInstructorVerification: false, version: 'test', buildCommit: 'test'
};
function paged(content, url, totalElements = content.length) {
  const size = Number(url.searchParams.get('size') ?? 20);
  return { content, totalElements, totalPages: Math.ceil(totalElements / size), size, number: Number(url.searchParams.get('page') ?? 0) };
}
function response(url) {
  switch (url.pathname) {
    case '/api/v1/meta': return meta;
    case '/api/v1/me': return { username: 'admin', displayName: 'Admin', actorType: 'HUMAN', roles: ['ROLE_ADMIN'] };
    case '/api/v1/courses': return paged([course], url);
    case '/api/v1/assignments': return paged([assignment], url);
    case '/api/v1/submissions': return paged([{ ...submission, status: url.searchParams.get('status') ?? submission.status, id: `s${url.searchParams.get('page') ?? 0}` }], url, 40);
    case '/api/v1/audit': return paged([{
      id: 'audit1', occurredAt: '2026-09-20T12:00:00Z', eventType: 'GRADING_COMPLETED',
      severity: 'INFO', actorType: 'SYSTEM', outcome: 'SUCCESS', detail: { result: 'Recorded' }
    }], url);
    case '/api/v1/reports/courses/c1': return report;
    case '/api/v1/reports/courses/c1/classes/cl1': return classReport;
    case '/api/v1/templates':
    case '/api/v1/test-suites': return paged([], url);
    case '/api/v1/runtimes': return ['Node.js 24', 'Java 25'].map((displayName, index) => ({
      id: `runtime${index}`, runtimeKey: `runtime${index}`, displayName, image: 'test', tag: '1',
      imageDigest: `sha256:${'a'.repeat(64)}`, testCommand: 'test', reportFormat: 'TAP', enabled: true,
      createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z'
    }));
    case '/api/v1/submissions/s1': return submission;
    case '/api/v1/submissions/older': return { ...submission, id: 'older' };
    case '/api/v1/reports/courses/c1/classes/cl1/students/st1': return {
      courseId: 'c1', classId: 'cl1', studentId: 'st1', studentUsername: 'student', fullName: student.fullName, status: 'SELF_REGISTERED',
      assignments: [{ assignmentId: 'a1', assignmentKey: 'strings', title: 'String utilities', bestPercent: 50, bestPoints: 5,
        latestSubmission: submission, latestGrading: { attempt: 1, status: 'COMPLETED', testsPassed: 0, testsTotal: 1,
          scorePercent: 0, pointsAwarded: 0, passed: false, tests: [{ visibility: 'PUBLIC', publicName: 'Public check', outcome: 'FAILED', studentMessage: 'Try again' }] }
      }]
    };
    case '/api/v1/materials/published': return { templateVersions: [], suiteVersions: [] };
    case '/api/v1/results/test-result': return {
      assignmentTitle: 'String utilities', courseName: course.name, commitSha: submission.commitSha,
      receivedAt: submission.receivedAt, verified: true, passed: 1, total: 2, score: 50,
      tests: [
        { public: true, name: 'Public test', outcome: 'PASSED', message: 'Passed' },
        { public: false, name: 'Do not disclose', category: 'Hidden test', outcome: 'FAILED', hint: 'Try again' }
      ]
    };
    default: throw new Error(`Unexpected API request: ${url.pathname}`);
  }
}

const server = await createServer({ server: { host: '127.0.0.1', port: 0, open: false } });
let browser;
try {
  await server.listen();
  const base = server.resolvedUrls.local[0];
  browser = await chromium.launch({
    ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE } : {})
  });
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  let retries = 0;
  await page.route('**/api/v1/**', async (route) => {
    try {
      if (route.request().method() === 'POST' && new URL(route.request().url()).pathname === '/api/v1/submissions/s0/regrade') {
        retries++;
        await route.fulfill({ status: 202, json: { gradingRunId: 'run1' } });
        return;
      }
      assert.equal(route.request().method(), 'GET');
      await route.fulfill({ json: response(new URL(route.request().url())) });
    } catch (error) {
      errors.push(error.message);
      await route.fulfill({ status: 500, json: { detail: error.message } });
    }
  });
  await page.addInitScript(() => {
    if (location.pathname === '/register/success') {
      history.replaceState({ usr: {
        result: { studentId: 'st1', studentUsername: 'student', fullName: 'Browser Test Student',
          status: 'SELF_REGISTERED', keyFingerprint: `SHA256:${'A'.repeat(43)}` }, courseKey: 'course'
      }, key: 'test', idx: 0 }, '');
    }
  });
  const paths = ['/submissions', '/admin/audit', '/reports/course/c1', '/submissions?status=INFRASTRUCTURE_ERROR'];
  let checks = 0;
  for (const colorScheme of ['light', 'dark']) {
    await page.emulateMedia({ colorScheme });
    for (const width of [320, 390, 768, 1024, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      for (const path of paths) {
        await page.goto(new URL(path, base).href);
        await page.locator('.MuiDataGrid-row').first().waitFor();
        const dimensions = await page.locator('.MuiDataGrid-root').evaluate((grid) => ({
          height: grid.getBoundingClientRect().height,
          viewport: grid.querySelector('.MuiDataGrid-virtualScroller').getBoundingClientRect().height,
          overflow: document.documentElement.scrollWidth > innerWidth,
          mainOverflow: document.querySelector('main').scrollWidth > document.querySelector('main').clientWidth
        }));
        assert.ok(dimensions.height >= 500, `${colorScheme} ${width} ${path}: grid collapsed`);
        assert.ok(dimensions.viewport >= 300, `${colorScheme} ${width} ${path}: rows are clipped`);
        assert.equal(dimensions.overflow || dimensions.mainOverflow, false, `${width} ${path}: page overflow`);
        checks++;
      }
      for (const path of ['/result/test-result', '/register/success']) {
        await page.goto(new URL(path, base).href);
        await page.locator('h1').waitFor();
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false,
          `${colorScheme} ${width} ${path}: page overflow`);
        assert.equal(await page.getByText('Do not disclose').count(), 0);
      }
    }
  }
  await page.goto(new URL('/submissions', base).href);
  await page.locator('.MuiDataGrid-row').first().waitFor();
  await page.getByRole('button', { name: 'Go to next page' }).click();
  await page.waitForFunction(() => document.querySelector('.MuiDataGrid-row[data-id="s1"]'));
  await page.goto(new URL('/assignments', base).href);
  await page.getByRole('heading', { name: 'Assignments', exact: true }).waitFor();
  await page.getByRole('navigation').getByRole('link', { name: 'Submissions', exact: true }).click();
  await page.locator('.MuiDataGrid-row').first().waitFor();
  assert.equal(await page.getByRole('heading', { name: 'Oops!' }).count(), 0);

  await page.goto(new URL('/assignments', base).href);
  await page.getByRole('button', { name: 'New Assignment' }).click();
  await page.getByRole('dialog').waitFor();
  const labelFits = await page.locator('.MuiDialogContent-root').evaluate((content) =>
    content.querySelector('label').getBoundingClientRect().top >= content.getBoundingClientRect().top
  );
  assert.ok(labelFits, 'Prefilled course label is clipped by the dialog');

  await page.getByRole('combobox', { name: 'Runtime', exact: true }).fill('Java');
  await page.getByRole('option', { name: 'Java 25' }).click();
  assert.equal(await page.getByRole('combobox', { name: 'Runtime', exact: true }).inputValue(), 'Java 25');

  await page.goto(new URL('/submissions?status=INFRASTRUCTURE_ERROR', base).href);
  await page.getByRole('button', { name: 'Retry grading' }).click();
  assert.equal(retries, 0);
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  assert.equal(retries, 0);
  await page.getByRole('button', { name: 'Retry grading' }).click();
  await page.getByRole('button', { name: 'Queue retry', exact: true }).click();
  await page.getByText('Grading retry queued.', { exact: true }).waitFor();
  assert.equal(retries, 1);

  await page.goto(new URL('/submissions/s1?classId=cl1', base).href);
  await page.getByText('Score: 0%', { exact: true }).waitFor();
  await page.getByText('Try again', { exact: true }).waitFor();
  await page.goto(new URL('/submissions/older?classId=cl1', base).href);
  await page.getByText(/not the latest attempt/).waitFor();
  assert.equal(await page.getByText('Score: 0%', { exact: true }).count(), 0);

  await page.goto(new URL('/courses/c1/classes/cl1', base).href);
  await page.getByRole('grid', { name: 'Class roster' }).waitFor();
  // Scroll to the offscreen column before checking every stacked attention badge.
  await page.locator('.MuiDataGrid-virtualScroller').evaluate((e) => { e.scrollLeft = e.scrollWidth; });
  await page.getByText('Latest failed', { exact: true }).waitFor();
  const badgesFit = await page.locator('.MuiDataGrid-cell[data-field="attention"]').evaluate((cell) => {
    const rect = cell.getBoundingClientRect();
    return [...cell.querySelectorAll('.MuiChip-root')].length === 3 &&
      [...cell.querySelectorAll('.MuiChip-root')].every((chip) => {
        const badge = chip.getBoundingClientRect();
        return badge.top >= rect.top && badge.bottom <= rect.bottom;
      });
  });
  assert.ok(badgesFit, 'Class attention badges are clipped');
  await page.getByRole('button', { name: '1 not started', exact: true }).click();
  await page.getByText('No students match these filters.', { exact: true }).waitFor();
  assert.ok(page.url().includes('attention=missing') && page.url().includes('assignment=strings'));
  assert.deepEqual(errors, [], 'Browser errors or missing fixtures');
  console.log(`UI browser checks passed (${checks} grid layouts, paging, navigation, dialog labels, attention badges, search, attention filters, contextual scores and confirmed retries)`);
} finally {
  await browser?.close();
  await server.close();
}
