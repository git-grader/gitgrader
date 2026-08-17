// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

/**
 * The cache keys every page and every invalidation shares.
 *
 * Keys used to be written out at each call site, which let the same data be filed under
 * two spellings: the materials page stored test suite versions under `testSuites` while
 * the assignment forms read them from `test-suites`, so publishing a version invalidated
 * a cache nobody was reading and the new version stayed missing from the assignment
 * dropdown until the page was reloaded.
 *
 * The second position is a role - `list`, `detail`, `choices` - rather than a bare value.
 * Without it a status filter, a course id and the literal `choices` all occupied the same
 * slot, so no invalidation could name one without matching the others.
 */
export const queryKeys = {
  meta: ['meta'] as const,
  me: ['me'] as const,
  dashboard: ['dashboard'] as const,
  availability: ['availability'] as const,
  result: (token: string) => ['result', token] as const,

  courses: {
    all: ['courses'] as const,
    list: (status: string, page: string, size: string) => ['courses', 'list', status, page, size] as const,
    choices: ['courses', 'choices'] as const,
    detail: (id: string) => ['courses', 'detail', id] as const,
    classes: (id: string) => ['courses', 'detail', id, 'classes'] as const
  },

  assignments: {
    all: ['assignments'] as const,
    list: (courseId: string, page: string, size: string) => ['assignments', 'list', courseId, page, size] as const,
    detail: (id: string) => ['assignments', 'detail', id] as const
  },

  submissions: {
    list: (courseId: string, page: string, size: string) => ['submissions', 'list', courseId, page, size] as const
  },

  students: {
    list: (page: string, size: string) => ['students', 'list', page, size] as const
  },

  templates: {
    all: ['templates'] as const,
    list: ['templates', 'list'] as const,
    versions: (templateId: string) => ['templates', 'detail', templateId, 'versions'] as const
  },

  testSuites: {
    all: ['test-suites'] as const,
    list: ['test-suites', 'list'] as const,
    versions: (suiteId: string) => ['test-suites', 'detail', suiteId, 'versions'] as const
  },

  runtimes: ['runtimes'] as const,

  audit: (page: string, size: string) => ['audit', page, size] as const,

  report: (courseId: string) => ['report', courseId] as const
};
