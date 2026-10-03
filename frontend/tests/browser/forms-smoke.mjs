// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

// Drives every form in the UI that changes data through a real Chromium browser and a
// stateful mock of the API. The point is to prove the write path end to end: the control
// is reachable, the request the page sends matches the contract, and the UI reflects the
// answer. Each successful write is recorded and asserted afterwards.

import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { createServer } from 'vite';

const meta = {
  name: 'GitGrader', organizationName: 'Test Organization', supportEmail: 'test@example.org',
  documentationUrl: 'https://example.org/docs', publicUrl: 'https://example.org',
  sshHost: 'example.org', sshPort: 2222, registrationEnabled: true,
  requireInstructorVerification: false, version: 'test', buildCommit: 'test'
};

const course = {
  id: 'c1', courseKey: 'course', name: 'Browser test course', description: null, semester: '2026FS',
  startsOn: null, endsOn: null, timezone: 'Europe/Zurich', status: 'ACTIVE',
  registrationOpensAt: null, registrationClosesAt: null, registrationEnabled: true
};

function assignment(overrides = {}) {
  return {
    id: 'a1', courseId: 'c1', assignmentKey: 'strings', title: 'String utilities', description: null,
    displayOrder: 1, status: 'DRAFT', mandatory: true, opensAt: '2030-01-01T12:00:00Z',
    dueAt: '2030-01-02T12:00:00Z', timezone: 'Europe/Zurich', maxPoints: 10, testCount: 4,
    passThreshold: 50, allowLate: false, templateVersionId: 'tv1', testSuiteVersionId: 'sv1',
    runtimeId: 'runtime0', timeoutSeconds: null, memoryLimitBytes: null, cpuLimit: null,
    pidLimit: null, networkEnabled: false, ...overrides
  };
}

const templateVersion = { id: 'tplv1', templateId: 'tpl1', versionLabel: '1.0.0', storagePath: 'x', contentHash: 'deadbeef', fileCount: 2, totalBytes: 100, publishedAt: null, publishedBy: null, createdAt: '2026-01-01T00:00:00Z' };
const suiteVersion = { id: 'suitev1', suiteId: 'suite1', versionLabel: '1.0.0', storagePath: 'x', contentHash: 'deadbeef', hiddenTestCount: 2, publicTestCount: 2, publishedAt: null, publishedBy: null, createdAt: '2026-01-01T00:00:00Z' };
const runtime = { id: 'runtime0', runtimeKey: 'runtime0', displayName: 'Java 25', image: 'registry.example.org/java', tag: '25', imageDigest: 'sha256:' + 'a'.repeat(64), installCommand: null, testCommand: 'mvn test', reportFormat: 'TAP', enabled: true, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' };

const state = {
  courses: [course],
  classes: [{ id: 'cl1', courseId: 'c1', classKey: 'class', name: 'Browser test class' }],
  students: [
    { id: 'st1', studentUsername: 'student', firstName: 'Browser', lastName: 'Student', email: 'student@example.org', status: 'SELF_REGISTERED' },
    { id: 'st2', studentUsername: 'second', firstName: 'Second', lastName: 'Student', email: 'second@example.org', status: 'VERIFIED_BY_INSTRUCTOR' }
  ],
  sshKeys: [{ id: 'k1', studentId: 'st1', label: 'Laptop', keyType: 'ED25519', publicKey: 'ssh-ed25519 AAAA k1', fingerprint: 'SHA256:AAAAk1', keyBits: null, comment: null, status: 'ACTIVE', origin: 'REGISTRATION', addedBy: null, revokedAt: null, revocationReason: null, replacedById: null, lastUsedAt: null, createdAt: '2026-01-01T00:00:00Z' }],
  assignments: [assignment()],
  extensions: [],
  templates: [{ id: 'tpl1', templateKey: 'tpl1', name: 'Template One', description: null }],
  templateVersions: [templateVersion],
  testSuites: [{ id: 'suite1', suiteKey: 'suite1', name: 'Suite One', description: null }],
  suiteVersions: [suiteVersion],
  runtimes: [runtime]
};

function paged(content, url) {
  const size = Number(url.searchParams.get('size') ?? 20);
  return { content, totalElements: content.length, totalPages: 1, size, number: 0 };
}

function statusResponse(id, status, body) {
  const student = state.students.find(s => s.id === id);
  return {
    id, studentUsername: student?.studentUsername ?? id, fullName: student ? `${student.firstName} ${student.lastName}` : id,
    email: student?.email ?? 'test@example.org', status, classLabel: null,
    registeredAt: '2030-01-01T00:00:00Z', reason: body?.reason
  };
}

function response(url) {
  const path = url.pathname;
  const studentMatch = path.match(/^\/api\/v1\/students\/([^/]+)$/);
  const templateVersions = path.match(/^\/api\/v1\/templates\/([^/]+)\/versions$/);
  const suiteVersions = path.match(/^\/api\/v1\/test-suites\/([^/]+)\/versions$/);
  if (path === '/api/v1/meta') return meta;
  if (path === '/api/v1/me') return { username: 'admin', displayName: 'Admin', actorType: 'HUMAN', roles: ['ROLE_ADMIN'] };
  if (path === '/api/v1/dashboard') return { courseCount: 1, studentCount: 2, openAssignmentCount: 1, runningGradingCount: 0, failedInfrastructureCount: 0 };
  if (path === '/api/v1/courses') return paged(state.courses, url);
  if (path === '/api/v1/courses/c1') return state.courses.find(c => c.id === 'c1');
  if (path === '/api/v1/courses/c1/classes') return state.classes;
  if (path === '/api/v1/students') return paged(state.students, url);
  if (studentMatch) {
    const id = studentMatch[1];
    return { student: state.students.find(s => s.id === id), sshKeys: state.sshKeys.filter(k => k.studentId === id) };
  }
  if (path === '/api/v1/assignments') return paged(state.assignments, url);
  if (path === '/api/v1/assignments/a1') return state.assignments.find(a => a.id === 'a1');
  if (path === '/api/v1/assignments/a1/extensions') return state.extensions;
  if (path === '/api/v1/materials/published') return {
    templateVersions: [{ id: 'tv1', templateName: 'Template One', versionLabel: '1.0.0' }],
    suiteVersions: [{ id: 'sv1', suiteName: 'Suite One', versionLabel: '1.0.0', hiddenTestCount: 2, publicTestCount: 2 }]
  };
  if (path === '/api/v1/runtimes') return state.runtimes;
  if (path === '/api/v1/templates') return paged(state.templates, url);
  if (templateVersions) return state.templateVersions.filter(v => v.templateId === templateVersions[1]);
  if (path === '/api/v1/test-suites') return paged(state.testSuites, url);
  if (suiteVersions) return state.suiteVersions.filter(v => v.suiteId === suiteVersions[1]);
  if (path === '/api/v1/registration/availability') return {
    open: true,
    courses: [{ courseKey: 'course', name: 'Browser test course', classes: [{ classKey: 'class', name: 'Browser test class' }] }]
  };
  throw new Error(`Unexpected API request: ${path}`);
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
  await page.setViewportSize({ width: 1440, height: 900 });
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));

  const writes = {};
  function record(method, path, payload) {
    const key = `${method} ${path}`;
    writes[key] = (writes[key] ?? 0) + 1;
    writes[`${key} body`] = payload;
  }
  function count(method, path) { return writes[`${method} ${path}`] ?? 0; }

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const method = request.method();
    const url = new URL(request.url());
    const path = url.pathname;
    try {
      if (method === 'GET') {
        await route.fulfill({ json: response(url) });
        return;
      }
      let body = null;
      try { body = request.postDataJSON(); } catch { body = request.postData(); }
      if (method === 'POST' && path === '/api/v1/courses') {
        record(method, path, body);
        const created = { ...course, ...body, id: 'c-new' };
        state.courses = [...state.courses, created];
        await route.fulfill({ status: 201, json: created }); return;
      }
      if (method === 'PUT' && path === '/api/v1/courses/c1') {
        record(method, path, body);
        await route.fulfill({ json: { ...course, ...body, id: 'c1' } }); return;
      }
      if (method === 'DELETE' && path === '/api/v1/courses/c1') {
        record(method, path, null);
        state.courses = state.courses.filter(c => c.id !== 'c1');
        await route.fulfill({ status: 204 }); return;
      }
      if (method === 'POST' && path === '/api/v1/courses/c1/classes') {
        record(method, path, body);
        const created = { id: 'cl-new', courseId: 'c1', ...body };
        state.classes = [...state.classes, created];
        await route.fulfill({ status: 201, json: created }); return;
      }
      if (method === 'PUT' && path === '/api/v1/courses/c1/classes/cl1') {
        record(method, path, body);
        await route.fulfill({ json: { id: 'cl1', courseId: 'c1', classKey: 'class', ...body } }); return;
      }
      if (method === 'DELETE' && path === '/api/v1/courses/c1/classes/cl1') {
        record(method, path, null);
        state.classes = state.classes.filter(c => c.id !== 'cl1');
        await route.fulfill({ status: 204 }); return;
      }
      if (method === 'PUT' && path === '/api/v1/students/st1') {
        record(method, path, body);
        Object.assign(state.students.find(s => s.id === 'st1'), body);
        await route.fulfill({ json: state.students.find(s => s.id === 'st1') }); return;
      }
      if (method === 'PATCH' && path === '/api/v1/students/st1/status') {
        record(method, path, body);
        const next = body.status === 'RESTORE' ? 'SELF_REGISTERED' : body.status;
        const student = state.students.find(s => s.id === 'st1');
        if (student) student.status = next;
        await route.fulfill({ json: statusResponse('st1', next, body) }); return;
      }
      if (method === 'POST' && path === '/api/v1/students/st1/keys') {
        record(method, path, body);
        const created = { id: 'k-new', studentId: 'st1', label: body.label, keyType: 'ED25519', publicKey: body.publicKey, fingerprint: 'SHA256:NEWKEY', status: 'ACTIVE', origin: 'INSTRUCTOR', createdAt: '2030-01-01T00:00:00Z' };
        state.sshKeys = [...state.sshKeys, created];
        await route.fulfill({ status: 201, json: created }); return;
      }
      if (method === 'POST' && path === '/api/v1/students/st1/keys/k1/revoke') {
        record(method, path, body);
        state.sshKeys = state.sshKeys.filter(k => k.id !== 'k1');
        await route.fulfill({ json: {} }); return;
      }
      if (method === 'POST' && path === '/api/v1/students/st1/results-overview/revoke') {
        record(method, path, null);
        await route.fulfill({ status: 204 }); return;
      }
      if (method === 'POST' && path === '/api/v1/assignments') {
        record(method, path, body);
        await route.fulfill({ status: 201, json: assignment({ ...body, id: 'a-new' }) }); return;
      }
      if (method === 'PUT' && path === '/api/v1/assignments/a1') {
        record(method, path, body);
        await route.fulfill({ json: { ...assignment(), ...body, id: 'a1' } }); return;
      }
      if (method === 'POST' && path === '/api/v1/assignments/a1/publish') {
        record(method, path, body);
        const published = assignment({ status: 'OPEN' });
        state.assignments = [published];
        await route.fulfill({ json: published }); return;
      }
      if (method === 'POST' && path === '/api/v1/assignments/a1/extensions') {
        record(method, path, body);
        const created = { id: 'ext1', assignmentId: 'a1', studentId: body.studentId, extendedDueAt: body.extendedDueAt, reason: body.reason, grantedBy: 'admin', grantedAt: '2030-01-01T00:00:00Z', revokedAt: null, revokedBy: null, createdAt: '2030-01-01T00:00:00Z' };
        state.extensions = [...state.extensions, created];
        await route.fulfill({ status: 201, json: created }); return;
      }
      if (method === 'DELETE' && path === '/api/v1/assignments/a1/extensions/ext1') {
        record(method, path, null);
        state.extensions = [];
        await route.fulfill({ status: 204 }); return;
      }
      if (method === 'POST' && path === '/api/v1/submissions/s1/regrade') {
        record(method, path, body);
        await route.fulfill({ status: 202, json: { gradingRunId: 'run1' } }); return;
      }
      if (method === 'POST' && path === '/api/v1/runtimes') {
        record(method, path, body);
        const created = { ...runtime, ...body, id: 'runtime-new', installCommand: body.installCommand ?? null };
        state.runtimes = [...state.runtimes, created];
        await route.fulfill({ status: 201, json: created }); return;
      }
      if (method === 'POST' && path === '/api/v1/templates') {
        record(method, path, body);
        await route.fulfill({ status: 201, json: { id: 'tpl-new', ...body } }); return;
      }
      if (method === 'POST' && path === '/api/v1/templates/tpl1/versions') {
        record(method, path, 'multipart');
        await route.fulfill({ status: 201, json: { ...templateVersion, id: 'tplv-new', versionLabel: '1.1' } }); return;
      }
      if (method === 'POST' && /^\/api\/v1\/templates\/versions\/[^/]+\/publish$/.test(path)) {
        record(method, path, body);
        const id = path.split('/')[5];
        state.templateVersions = state.templateVersions.map(v => v.id === id ? { ...v, publishedAt: '2030-01-01T00:00:00Z' } : v);
        await route.fulfill({ json: { ...templateVersion, id, publishedAt: '2030-01-01T00:00:00Z' } }); return;
      }
      if (method === 'POST' && path === '/api/v1/test-suites') {
        record(method, path, body);
        await route.fulfill({ status: 201, json: { id: 'suite-new', ...body } }); return;
      }
      if (method === 'POST' && path === '/api/v1/test-suites/suite1/versions') {
        record(method, path, 'multipart');
        await route.fulfill({ status: 201, json: { ...suiteVersion, id: 'suitev-new', versionLabel: '1.1' } }); return;
      }
      if (method === 'POST' && /^\/api\/v1\/test-suites\/versions\/[^/]+\/publish$/.test(path)) {
        record(method, path, body);
        const id = path.split('/')[5];
        state.suiteVersions = state.suiteVersions.map(v => v.id === id ? { ...v, hiddenTestCount: body.hiddenTestCount, publicTestCount: body.publicTestCount, publishedAt: '2030-01-01T00:00:00Z' } : v);
        await route.fulfill({ json: { ...suiteVersion, id, hiddenTestCount: body.hiddenTestCount, publicTestCount: body.publicTestCount, publishedAt: '2030-01-01T00:00:00Z' } }); return;
      }
      if (method === 'POST' && path === '/api/v1/registration') {
        record(method, path, body);
        await route.fulfill({ status: 201, json: { studentId: 'st-new', studentUsername: body.studentUsername, fullName: `${body.firstName} ${body.lastName}`, status: 'SELF_REGISTERED', keyFingerprint: 'SHA256:NEWREG' } }); return;
      }
      throw new Error(`Unexpected ${method} ${path}`);
    } catch (error) {
      errors.push(error.message);
      await route.fulfill({ status: 500, json: { detail: error.message } });
    }
  });

  const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const byLabel = (scope, text) => scope.getByLabel(new RegExp(`^${escapeRegExp(text)}(\\s*\\*)?$`));

  async function goto(path) {
    await page.goto(new URL(path, base).href);
    await page.locator('h1').waitFor();
    await page.waitForFunction(() => document.querySelectorAll('.MuiCircularProgress-root').length === 0);
  }
  async function acceptNextDialog(action = 'accept') {
    page.once('dialog', (dialog) => (action === 'accept' ? dialog.accept() : dialog.dismiss()));
  }
  function acceptNextPrompt(text) {
    page.once('dialog', (dialog) => dialog.accept(text));
  }

  // ---- Courses: create, edit, class create/edit/delete, delete ----
  await goto('/courses');
  await page.getByRole('button', { name: 'New Course' }).click();
  let dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Course Key').fill('newcourse');
  await byLabel(dialog, 'Name').fill('Newly created course');
  await dialog.getByRole('button', { name: 'Create', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });

  await goto('/courses/c1');
  await page.getByRole('button', { name: 'Edit Course' }).click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Name').fill('Renamed course');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });

  await page.getByRole('button', { name: 'Add Class' }).click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Class Key').fill('cl2');
  await byLabel(dialog, 'Name').fill('Class Two');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });

  await page.getByRole('button', { name: 'Edit', exact: true }).first().click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Name').fill('Class One renamed');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });

  await page.getByRole('button', { name: 'Edit', exact: true }).first().click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  acceptNextDialog();
  await dialog.getByRole('button', { name: 'Delete Class' }).click();
  await dialog.waitFor({ state: 'hidden' });

  // ---- Students list: verify, suspend, restore, archive ----
  await goto('/students');
  await page.locator('.MuiDataGrid-row').first().waitFor();
  const st1 = page.locator('.MuiDataGrid-row[data-id="st1"]');
  await st1.getByRole('button', { name: 'Verify', exact: true }).click();
  await page.locator('.MuiDataGrid-row[data-id="st1"]').getByRole('button', { name: 'Suspend', exact: true }).waitFor();
  acceptNextDialog();
  await page.locator('.MuiDataGrid-row[data-id="st1"]').getByRole('button', { name: 'Suspend', exact: true }).click();
  await page.locator('.MuiDataGrid-row[data-id="st1"]').getByRole('button', { name: 'Restore', exact: true }).waitFor();
  await page.locator('.MuiDataGrid-row[data-id="st1"]').getByRole('button', { name: 'Restore', exact: true }).click();
  await page.locator('.MuiDataGrid-row[data-id="st1"]').getByRole('button', { name: 'Archive', exact: true }).waitFor();
  acceptNextDialog();
  await page.locator('.MuiDataGrid-row[data-id="st1"]').getByRole('button', { name: 'Archive', exact: true }).click();
  await page.locator('.MuiDataGrid-row[data-id="st1"]').getByRole('button', { name: 'Restore', exact: true }).waitFor();
  acceptNextDialog();
  await page.locator('.MuiDataGrid-row[data-id="st1"]').getByRole('button', { name: 'Restore', exact: true }).click();

  // ---- Student detail: register key, revoke key, status, edit ----
  await goto('/students/st1');
  await byLabel(page, 'Label').fill('Workstation');
  await byLabel(page, 'Public Key').fill('ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIWorkstation staff@example.org');
  await byLabel(page, 'Reason').fill('Replacement laptop');
  await page.getByRole('button', { name: 'Register key' }).click();
  await page.getByText('Workstation', { exact: true }).waitFor();
  acceptNextPrompt('Revoked by instructor');
  await page.getByRole('button', { name: 'Revoke', exact: true }).first().click();
  await page.getByText('Laptop', { exact: true }).waitFor({ state: 'hidden' });

  const statusPanel = page.getByRole('region', { name: 'Student status' });
  await statusPanel.getByRole('button', { name: 'Verify', exact: true }).click();
  acceptNextPrompt('Suspended by instructor');
  await statusPanel.getByRole('button', { name: 'Suspend', exact: true }).click();
  await statusPanel.getByRole('button', { name: 'Restore', exact: true }).click();
  acceptNextDialog();
  await statusPanel.getByRole('button', { name: 'Archive', exact: true }).click();

  acceptNextDialog();
  await page.getByRole('button', { name: 'Reset results link' }).click();
  await page.getByText(/results link was reset/).waitFor();

  await byLabel(page, 'First Name').fill('Changed');
  await byLabel(page, 'Last Name').fill('Name');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await page.waitForURL('**/students');

  // ---- Assignments: create ----
  await goto('/assignments');
  await page.getByRole('button', { name: 'New Assignment' }).click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Key').fill('created-assignment');
  await byLabel(dialog, 'Title').fill('Created assignment');
  await dialog.getByRole('button', { name: 'Create', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });

  // ---- Assignment detail: save config, publish, grant/revoke extension ----
  await goto('/assignments/a1');
  await page.getByRole('spinbutton', { name: 'Test Count', exact: true }).fill('4');
  await page.getByRole('button', { name: 'Save Configuration', exact: true }).click();
  await page.getByText('Assignment updated successfully.', { exact: true }).waitFor();

  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await page.getByRole('button', { name: 'Published', exact: true }).waitFor();

  await byLabel(page, 'Student ID').fill('st1');
  await byLabel(page, 'Extended Due At').fill('2030-03-01T12:00');
  await byLabel(page, 'Reason').fill('Medical leave');
  await page.getByRole('button', { name: 'Grant extension', exact: true }).click();
  await page.getByText('Medical leave', { exact: true }).waitFor();
  acceptNextDialog();
  await page.getByRole('button', { name: 'Revoke', exact: true }).first().click();

  // ---- Admin runtimes: create ----
  await goto('/admin/runtimes');
  await page.getByRole('button', { name: 'New Runtime' }).click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Key').fill('node-e2e');
  await byLabel(dialog, 'Display Name').fill('Node E2E');
  await byLabel(dialog, 'Image').fill('registry.example.org/node');
  await byLabel(dialog, 'Tag').fill('24');
  await byLabel(dialog, 'Image Digest').fill('sha256:' + 'b'.repeat(64));
  await byLabel(dialog, 'Test Command').fill('npm test');
  await dialog.getByRole('button', { name: 'Create Runtime', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });

  // ---- Materials: template + upload + publish, suite + upload + publish ----
  await goto('/materials');
  await page.getByRole('button', { name: 'New Template' }).click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Template Key').fill('tpl2');
  await byLabel(dialog, 'Name').fill('Template Two');
  await dialog.getByRole('button', { name: 'Create', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });

  await page.getByRole('button', { name: 'Upload Version' }).first().click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Version Label').fill('1.1');
  await dialog.locator('input[type="file"]').setInputFiles({ name: 'version.zip', mimeType: 'application/zip', buffer: Buffer.from('PK') });
  await dialog.getByRole('button', { name: 'Upload', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: 'Publish', exact: true }).first().click();
  await page.getByText(/^Published /).first().waitFor();

  await page.getByRole('tab', { name: 'Test Suites' }).click();
  await page.getByRole('button', { name: 'New Test Suite' }).click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Suite Key').fill('suite2');
  await byLabel(dialog, 'Name').fill('Suite Two');
  await dialog.getByRole('button', { name: 'Create', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });

  await page.getByRole('button', { name: 'Upload Version' }).first().click();
  dialog = page.getByRole('dialog');
  await dialog.waitFor();
  await byLabel(dialog, 'Version Label').fill('1.1');
  await dialog.locator('input[type="file"]').setInputFiles({ name: 'suite.zip', mimeType: 'application/zip', buffer: Buffer.from('PK') });
  await dialog.getByRole('button', { name: 'Upload', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: 'Publish', exact: true }).first().click();
  await page.getByRole('button', { name: 'Confirm Publish', exact: true }).click();
  await page.getByText(/^Published /).first().waitFor();

  // ---- Registration (public form) ----
  await goto('/register');
  await byLabel(page, 'First Name').fill('Ada');
  await byLabel(page, 'Last Name').fill('Lovelace');
  await byLabel(page, 'Student ID / Username').fill('ada1');
  await byLabel(page, 'Email').fill('ada@example.org');
  await page.getByRole('combobox', { name: 'Course' }).click();
  await page.getByRole('option', { name: 'Browser test course' }).click();
  await page.getByRole('combobox', { name: 'Class' }).click();
  await page.getByRole('option', { name: 'Browser test class' }).click();
  await byLabel(page, 'SSH Public Key').fill('ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIRegister ada@example.org');
  await page.getByRole('button', { name: 'Register', exact: true }).click();
  await page.waitForURL('**/register/success');
  await page.getByRole('heading', { name: 'Registration Successful' }).waitFor();

  // ---- Delete course last: c1 is no longer needed ----
  await goto('/courses/c1');
  await page.getByRole('button', { name: 'Edit Course' }).click();
  await page.getByRole('dialog').waitFor();
  acceptNextDialog();
  await page.getByRole('button', { name: 'Delete Course' }).click();
  await page.waitForURL('**/courses');

  // ---- Assert every write happened with the expected contract ----
  const expected = [
    ['POST', '/api/v1/courses'], ['PUT', '/api/v1/courses/c1'], ['DELETE', '/api/v1/courses/c1'],
    ['POST', '/api/v1/courses/c1/classes'], ['PUT', '/api/v1/courses/c1/classes/cl1'], ['DELETE', '/api/v1/courses/c1/classes/cl1'],
    ['PATCH', '/api/v1/students/st1/status'], ['PUT', '/api/v1/students/st1'],
    ['POST', '/api/v1/students/st1/keys'], ['POST', '/api/v1/students/st1/keys/k1/revoke'],
    ['POST', '/api/v1/students/st1/results-overview/revoke'],
    ['POST', '/api/v1/assignments'], ['PUT', '/api/v1/assignments/a1'],
    ['POST', '/api/v1/assignments/a1/publish'], ['POST', '/api/v1/assignments/a1/extensions'],
    ['DELETE', '/api/v1/assignments/a1/extensions/ext1'], ['POST', '/api/v1/runtimes'],
    ['POST', '/api/v1/templates'], ['POST', '/api/v1/templates/tpl1/versions'], ['POST', '/api/v1/templates/versions/tplv1/publish'],
    ['POST', '/api/v1/test-suites'], ['POST', '/api/v1/test-suites/suite1/versions'], ['POST', '/api/v1/test-suites/versions/suitev1/publish'],
    ['POST', '/api/v1/registration']
  ];
  for (const [method, path] of expected) {
    assert.ok(count(method, path) >= 1, `expected a ${method} ${path} write, saw ${count(method, path)}`);
  }
  assert.deepEqual(errors, [], 'unexpected browser errors or unmocked requests');
  console.log(`Form writes verified (${expected.length} mutating endpoints): ${expected.map(([m, p]) => `${m} ${p}`).join(', ')}`);
} finally {
  await browser?.close();
  await server.close();
}
