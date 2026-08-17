// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { z } from 'zod';
import { fetchApi, postMultipart } from './client';

/**
 * Reads a response and checks it against the schema that describes it.
 *
 * The schemas used to be type sources only: every call was `res.json() as T`, so a
 * server that renamed a field produced `undefined` several components later rather than
 * an error at the boundary, and the nullability worked out here was never enforced on
 * anything. Parsing means a contract break surfaces as a failed query on the page that
 * asked, which the error notices already know how to show.
 */
async function readJson<S extends z.ZodType>(path: string, schema: S): Promise<z.output<S>> {
  return schema.parse(await fetchApi<unknown>(path));
}

async function sendJson<S extends z.ZodType>(
  method: 'POST' | 'PUT' | 'PATCH',
  path: string,
  body: unknown,
  schema: S
): Promise<z.output<S>> {
  const received = await fetchApi<unknown>(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return schema.parse(received);
}

async function uploadJson<S extends z.ZodType>(path: string, formData: FormData, schema: S): Promise<z.output<S>> {
  return schema.parse(await postMultipart<unknown>(path, formData));
}

function queryString(params?: Record<string, string>): string {
  if (!params) return '';
  const qs = new URLSearchParams(params).toString();
  return qs ? `?${qs}` : '';
}

/**
 * A status is deliberately not an enum on the way in.
 *
 * The chips render a value they do not recognise verbatim, on purpose: a newer server
 * that adds a status should show it rather than fail the whole page. Parsing it as a
 * closed set would turn that into the blank screen the chips exist to avoid.
 */
const status = z.string();

export const MetaSchema = z.object({
  name: z.string(),
  organizationName: z.string(),
  supportEmail: z.string(),
  documentationUrl: z.string(),
  publicUrl: z.string(),
  sshHost: z.string(),
  sshPort: z.number(),
  registrationEnabled: z.boolean(),
  version: z.string()
});
export type Meta = z.infer<typeof MetaSchema>;

// `classKey` is nullable on the server: a course may offer no classes at all, and
// requiring one here made such a course impossible to register for through a form whose
// class dropdown was empty and required at the same time.
export const RegistrationRequestSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  studentNumber: z.string().min(1, 'Student number is required'),
  email: z.email('Invalid email'),
  courseKey: z.string().min(1, 'Course is required'),
  classKey: z.string().nullish(),
  publicKey: z.string().min(1, 'Public key is required')
});
export type RegistrationRequest = z.infer<typeof RegistrationRequestSchema>;

export const RegistrationResponseSchema = z.object({
  studentId: z.string(),
  studentNumber: z.string(),
  fullName: z.string(),
  status,
  keyFingerprint: z.string(),
  repositories: z.array(z.object({
    assignmentKey: z.string(),
    assignmentTitle: z.string(),
    cloneUrl: z.string()
  }))
});
export type RegistrationResponse = z.infer<typeof RegistrationResponseSchema>;

export const AvailabilitySchema = z.object({
  open: z.boolean(),
  opensAt: z.string().nullish(),
  closesAt: z.string().nullish(),
  courses: z.array(z.object({
    courseKey: z.string(),
    name: z.string(),
    classes: z.array(z.object({
      classKey: z.string(),
      name: z.string()
    }))
  }))
});
export type Availability = z.infer<typeof AvailabilitySchema>;

// The server sends every absent field as an explicit null rather than omitting it, so
// these are nullish and not optional. The distinction is not cosmetic: `.optional()`
// describes a value that is missing, and a schema saying so would reject every response
// this endpoint actually sends.
export const PublicResultSchema = z.object({
  assignmentTitle: z.string(),
  courseName: z.string(),
  commitSha: z.string(),
  receivedAt: z.string(),
  verified: z.boolean(),
  passed: z.number(),
  total: z.number(),
  score: z.number().nullish(),
  tests: z.array(z.object({
    public: z.boolean(),
    name: z.string().nullish(),
    category: z.string().nullish(),
    outcome: z.string(),
    message: z.string().nullish(),
    hint: z.string().nullish()
  }))
});
export type PublicResult = z.infer<typeof PublicResultSchema>;

export const MeSchema = z.object({
  username: z.string(),
  displayName: z.string(),
  actorType: z.string(),
  roles: z.array(z.string())
});
export type Me = z.infer<typeof MeSchema>;

export const DashboardSchema = z.object({
  courseCount: z.number(),
  studentCount: z.number(),
  openAssignmentCount: z.number(),
  runningGradingCount: z.number(),
  failedInfrastructureCount: z.number(),
  recentActivity: z.array(z.unknown())
});
export type Dashboard = z.infer<typeof DashboardSchema>;

export const PageSchema = <T extends z.ZodType>(itemSchema: T) => z.object({
  content: z.array(itemSchema),
  totalElements: z.number(),
  totalPages: z.number(),
  size: z.number(),
  number: z.number()
});

export type Page<T> = { content: T[]; totalElements: number; totalPages: number; size: number; number: number };

export const StudentSummarySchema = z.object({
  id: z.string(),
  studentNumber: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
  status
});
export type StudentSummary = z.infer<typeof StudentSummarySchema>;

export const CourseViewSchema = z.object({
  id: z.string(),
  courseKey: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  semester: z.string().nullish(),
  startsOn: z.string().nullish(),
  endsOn: z.string().nullish(),
  timezone: z.string(),
  status,
  registrationOpensAt: z.string().nullish(),
  registrationClosesAt: z.string().nullish(),
  registrationEnabled: z.boolean()
});
export type CourseView = z.infer<typeof CourseViewSchema>;

export const CourseDefinitionSchema = z.object({
  courseKey: z.string().min(1, 'Course key is required'),
  name: z.string().min(1, 'Name is required'),
  description: z.string().nullish(),
  semester: z.string().nullish(),
  startsOn: z.string().nullish(),
  endsOn: z.string().nullish(),
  timezone: z.string().min(1, 'Timezone is required'),
  status: z.string().min(1, 'Status is required'),
  registrationOpensAt: z.string().nullish(),
  registrationClosesAt: z.string().nullish(),
  registrationEnabled: z.boolean()
});
export type CourseDefinition = z.infer<typeof CourseDefinitionSchema>;

export const ClassSchema = z.object({
  id: z.string(),
  courseId: z.string(),
  classKey: z.string(),
  name: z.string()
});
export type Class = z.infer<typeof ClassSchema>;

export const ClassDefinitionSchema = z.object({
  classKey: z.string().min(1, 'Class key is required'),
  name: z.string().min(1, 'Name is required')
});
export type ClassDefinition = z.infer<typeof ClassDefinitionSchema>;

export const AssignmentDefinitionSchema = z.object({
  courseId: z.string().min(1, 'Course ID is required'),
  assignmentKey: z.string().min(1, 'Assignment key is required'),
  title: z.string().min(1, 'Title is required'),
  description: z.string().nullish(),
  displayOrder: z.number().int(),
  status: z.string().min(1, 'Status is required'),
  mandatory: z.boolean(),
  opensAt: z.string().nullish(),
  dueAt: z.string().nullish(),
  timezone: z.string().nullish(),
  maxPoints: z.number().min(0),
  testCount: z.number().int().min(0),
  passThreshold: z.number().min(0),
  allowLate: z.boolean(),
  templateVersionId: z.string().nullish(),
  testSuiteVersionId: z.string().nullish(),
  runtimeId: z.string().nullish(),
  timeoutSeconds: z.number().int().min(1).nullish(),
  memoryLimitBytes: z.number().int().min(1).nullish(),
  cpuLimit: z.number().min(0).nullish(),
  pidLimit: z.number().int().min(1).nullish(),
  networkEnabled: z.boolean()
});
export type AssignmentDefinition = z.infer<typeof AssignmentDefinitionSchema>;

// Mirrors AssignmentView. The list and the detail endpoint both return it in full, so
// there is one schema rather than a summary that silently under-describes the same body.
// `timezone` is nullable on the server; claiming otherwise made a null read as a string.
export const AssignmentSchema = z.object({
  id: z.string(),
  courseId: z.string(),
  assignmentKey: z.string(),
  title: z.string(),
  description: z.string().nullish(),
  displayOrder: z.number(),
  status,
  mandatory: z.boolean(),
  opensAt: z.string().nullish(),
  dueAt: z.string().nullish(),
  timezone: z.string().nullish(),
  maxPoints: z.number(),
  testCount: z.number(),
  passThreshold: z.number(),
  allowLate: z.boolean(),
  templateVersionId: z.string().nullish(),
  testSuiteVersionId: z.string().nullish(),
  runtimeId: z.string().nullish(),
  timeoutSeconds: z.number().nullish(),
  memoryLimitBytes: z.number().nullish(),
  cpuLimit: z.number().nullish(),
  pidLimit: z.number().nullish(),
  networkEnabled: z.boolean()
});
export type AssignmentDetail = z.infer<typeof AssignmentSchema>;

export const TemplateSchema = z.object({
  id: z.string(),
  templateKey: z.string(),
  name: z.string(),
  description: z.string().nullish()
});
export type Template = z.infer<typeof TemplateSchema>;

export const TemplateDefinitionSchema = z.object({
  templateKey: z.string().min(1, 'Template key is required'),
  name: z.string().min(1, 'Name is required'),
  description: z.string().optional()
});
export type TemplateDefinition = z.infer<typeof TemplateDefinitionSchema>;

export const TemplateVersionSchema = z.object({
  id: z.string(),
  templateId: z.string(),
  versionLabel: z.string(),
  storagePath: z.string(),
  contentHash: z.string(),
  fileCount: z.number(),
  totalBytes: z.number(),
  publishedAt: z.string().nullish(),
  publishedBy: z.string().nullish(),
  createdAt: z.string()
});
export type TemplateVersion = z.infer<typeof TemplateVersionSchema>;

export const TestSuiteSchema = z.object({
  id: z.string(),
  suiteKey: z.string(),
  name: z.string(),
  description: z.string().nullish()
});
export type TestSuite = z.infer<typeof TestSuiteSchema>;

export const TestSuiteDefinitionSchema = z.object({
  suiteKey: z.string().min(1, 'Suite key is required'),
  name: z.string().min(1, 'Name is required'),
  description: z.string().optional()
});
export type TestSuiteDefinition = z.infer<typeof TestSuiteDefinitionSchema>;

export const TestSuiteVersionSchema = z.object({
  id: z.string(),
  suiteId: z.string(),
  versionLabel: z.string(),
  storagePath: z.string(),
  contentHash: z.string(),
  hiddenTestCount: z.number(),
  publicTestCount: z.number(),
  publishedAt: z.string().nullish(),
  publishedBy: z.string().nullish(),
  createdAt: z.string()
});
export type TestSuiteVersion = z.infer<typeof TestSuiteVersionSchema>;

// Mirrors SubmissionView. `late` and `signatureStatus` were missing, which is why the
// submissions list could not say whether an attempt was late or whether its commit
// signature verified - the two facts the product exists to record.
export const SubmissionSchema = z.object({
  id: z.string(),
  repositoryId: z.string(),
  repositoryPath: z.string().nullish(),
  studentId: z.string(),
  courseId: z.string(),
  assignmentId: z.string(),
  commitSha: z.string(),
  shortCommitSha: z.string(),
  gitRef: z.string(),
  commitMessage: z.string().nullish(),
  receivedAt: z.string(),
  signatureStatus: z.string(),
  signatureFingerprint: z.string().nullish(),
  status,
  late: z.boolean(),
  effectiveDueAt: z.string().nullish(),
  rejectionReason: z.string().nullish(),
  runtimeImageDigest: z.string().nullish()
});
export type Submission = z.infer<typeof SubmissionSchema>;

export const CourseReportSchema = z.object({
  courseId: z.string(),
  totalMandatoryAssignments: z.number(),
  totalPointsAvailable: z.number(),
  students: z.array(z.object({
    studentId: z.string(),
    studentNumber: z.string(),
    fullName: z.string(),
    fullyCompleted: z.number(),
    partiallyCompleted: z.number(),
    notStarted: z.number(),
    completionRate: z.number(),
    pointsEarned: z.number(),
    pointsRate: z.number(),
    totalPoints: z.number(),
    submissionCount: z.number(),
    lastActivityAt: z.string().nullish(),
    // Note the scale: these are 0-100 percentages, unlike completionRate and pointsRate
    // above, which are 0-1 fractions.
    assignments: z.record(z.string(), z.object({
      percent: z.number(),
      points: z.number()
    }))
  }))
});
export type CourseReport = z.infer<typeof CourseReportSchema>;

export const AuditEventSchema = z.object({
  id: z.string(),
  occurredAt: z.string(),
  eventType: z.string(),
  severity: z.string(),
  actorType: z.string(),
  actorId: z.string().nullish(),
  actorName: z.string().nullish(),
  subjectType: z.string().nullish(),
  subjectId: z.string().nullish(),
  courseId: z.string().nullish(),
  outcome: z.string(),
  sourceIpHash: z.string().nullish(),
  correlationId: z.string().nullish(),
  detail: z.record(z.string(), z.unknown()).nullish()
});
export type AuditEvent = z.infer<typeof AuditEventSchema>;

/**
 * The report formats a runtime can produce, exactly as the server names them.
 *
 * This used to be free text with a default of `JUNIT`, which is not a value the server
 * accepts: the prefilled form was rejected on submit, and since a runtime is required
 * before any assignment can be published, that blocked setting an instance up at all.
 */
export const ReportFormatSchema = z.enum(['JUNIT_XML', 'TAP', 'JSON_SUMMARY']);
export type ReportFormat = z.infer<typeof ReportFormatSchema>;
export const REPORT_FORMATS = ReportFormatSchema.options;

export const RuntimeSchema = z.object({
  id: z.string(),
  runtimeKey: z.string(),
  displayName: z.string(),
  image: z.string(),
  tag: z.string(),
  imageDigest: z.string(),
  installCommand: z.string().nullish(),
  testCommand: z.string(),
  reportFormat: z.string(),
  enabled: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string()
});
export type Runtime = z.infer<typeof RuntimeSchema>;

export const RuntimeDefinitionSchema = z.object({
  runtimeKey: z.string().min(1, 'Required'),
  displayName: z.string().min(1, 'Required'),
  image: z.string().min(1, 'Required'),
  tag: z.string().min(1, 'Required').refine(val => val !== 'latest', { message: "tag 'latest' is not reproducible" }),
  imageDigest: z.string().regex(/^sha256:[a-f0-9]{64}$/, 'Must be a valid sha256 digest'),
  installCommand: z.string().nullish(),
  testCommand: z.string().min(1, 'Required'),
  reportFormat: ReportFormatSchema,
  enabled: z.boolean()
});
export type RuntimeDefinition = z.infer<typeof RuntimeDefinitionSchema>;

const CoursePageSchema = PageSchema(CourseViewSchema);
const AssignmentPageSchema = PageSchema(AssignmentSchema);
const SubmissionPageSchema = PageSchema(SubmissionSchema);
const StudentPageSchema = PageSchema(StudentSummarySchema);
const TemplatePageSchema = PageSchema(TemplateSchema);
const TestSuitePageSchema = PageSchema(TestSuiteSchema);
const AuditPageSchema = PageSchema(AuditEventSchema);

export const api = {
  getMeta: () => readJson('/api/v1/meta', MetaSchema),
  getAvailability: () => readJson('/api/v1/registration/availability', AvailabilitySchema),
  register: (req: RegistrationRequest) => sendJson('POST', '/api/v1/registration', req, RegistrationResponseSchema),
  getResult: (token: string) => readJson(`/api/v1/results/${encodeURIComponent(token)}`, PublicResultSchema),
  getMe: () => readJson('/api/v1/me', MeSchema),
  // Spring Security's logout filter, configured in WebSecurityConfig, listens at
  // /logout rather than under /api. This previously pointed at /api/v1/auth/logout,
  // which no controller serves: the call failed and the session stayed alive while the
  // UI behaved as though the user had signed out.
  logout: async (): Promise<void> => {
    await fetchApi<unknown>('/logout', { method: 'POST' });
  },
  getDashboard: () => readJson('/api/v1/dashboard', DashboardSchema),

  getStudents: (params?: Record<string, string>) =>
    readJson(`/api/v1/students${queryString(params)}`, StudentPageSchema),

  getAssignments: (params?: Record<string, string>) =>
    readJson(`/api/v1/assignments${queryString(params)}`, AssignmentPageSchema),
  getAssignment: (id: string) => readJson(`/api/v1/assignments/${id}`, AssignmentSchema),
  createAssignment: (req: AssignmentDefinition) => sendJson('POST', '/api/v1/assignments', req, AssignmentSchema),
  updateAssignment: (id: string, req: AssignmentDefinition) =>
    sendJson('PUT', `/api/v1/assignments/${id}`, req, AssignmentSchema),
  publishAssignment: (id: string) => sendJson('POST', `/api/v1/assignments/${id}/publish`, undefined, AssignmentSchema),

  getSubmissions: (params?: Record<string, string>) =>
    readJson(`/api/v1/submissions${queryString(params)}`, SubmissionPageSchema),

  getCourseReport: (courseId: string) => readJson(`/api/v1/reports/courses/${courseId}`, CourseReportSchema),

  getCourses: (params?: Record<string, string>) => readJson(`/api/v1/courses${queryString(params)}`, CoursePageSchema),
  getCourse: (id: string) => readJson(`/api/v1/courses/${id}`, CourseViewSchema),
  createCourse: (req: CourseDefinition) => sendJson('POST', '/api/v1/courses', req, CourseViewSchema),
  updateCourse: (id: string, req: CourseDefinition) => sendJson('PUT', `/api/v1/courses/${id}`, req, CourseViewSchema),
  getCourseClasses: (id: string) => readJson(`/api/v1/courses/${id}/classes`, z.array(ClassSchema)),
  createClass: (id: string, req: ClassDefinition) => sendJson('POST', `/api/v1/courses/${id}/classes`, req, ClassSchema),
  updateClass: (id: string, classId: string, req: ClassDefinition) =>
    sendJson('PUT', `/api/v1/courses/${id}/classes/${classId}`, req, ClassSchema),

  getTemplates: (params?: Record<string, string>) =>
    readJson(`/api/v1/templates${queryString(params)}`, TemplatePageSchema),
  createTemplate: (req: TemplateDefinition) => sendJson('POST', '/api/v1/templates', req, TemplateSchema),
  getTemplateVersions: (id: string) => readJson(`/api/v1/templates/${id}/versions`, z.array(TemplateVersionSchema)),
  createTemplateVersion: (id: string, formData: FormData) =>
    uploadJson(`/api/v1/templates/${id}/versions`, formData, TemplateVersionSchema),
  publishTemplateVersion: (versionId: string) =>
    sendJson('POST', `/api/v1/templates/versions/${versionId}/publish`, undefined, TemplateVersionSchema),

  getTestSuites: (params?: Record<string, string>) =>
    readJson(`/api/v1/test-suites${queryString(params)}`, TestSuitePageSchema),
  createTestSuite: (req: TestSuiteDefinition) => sendJson('POST', '/api/v1/test-suites', req, TestSuiteSchema),
  getTestSuiteVersions: (id: string) => readJson(`/api/v1/test-suites/${id}/versions`, z.array(TestSuiteVersionSchema)),
  createTestSuiteVersion: (id: string, formData: FormData) =>
    uploadJson(`/api/v1/test-suites/${id}/versions`, formData, TestSuiteVersionSchema),
  publishTestSuiteVersion: (versionId: string, req: { hiddenTestCount: number; publicTestCount: number }) =>
    sendJson('POST', `/api/v1/test-suites/versions/${versionId}/publish`, req, TestSuiteVersionSchema),

  getAuditLog: (params?: Record<string, string>) =>
    readJson(`/api/v1/audit${queryString({ sort: 'occurredAt,desc', ...params })}`, AuditPageSchema),

  getRuntimes: () => readJson('/api/v1/runtimes', z.array(RuntimeSchema)),
  createRuntime: (req: RuntimeDefinition) => sendJson('POST', '/api/v1/runtimes', req, RuntimeSchema)
};
