// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { z } from 'zod';
import { fetchApi, postMultipart } from './client';

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

export const RegistrationRequestSchema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  studentNumber: z.string().min(1, 'Student number is required'),
  email: z.email('Invalid email'),
  courseKey: z.string().min(1, 'Course is required'),
  classKey: z.string().min(1, 'Class is required'),
  publicKey: z.string().min(1, 'Public key is required')
});
export type RegistrationRequest = z.infer<typeof RegistrationRequestSchema>;

export const RegistrationResponseSchema = z.object({
  studentId: z.string(),
  studentNumber: z.string(),
  fullName: z.string(),
  status: z.string(),
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
  opensAt: z.string().optional().nullable(),
  closesAt: z.string().optional().nullable(),
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
// this endpoint actually sends the moment anything started parsing with it.
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
  roles: z.array(z.string())
});
export type Me = z.infer<typeof MeSchema>;

export const DashboardSchema = z.object({
  courseCount: z.number(),
  studentCount: z.number(),
  openAssignmentCount: z.number(),
  runningGradingCount: z.number(),
  failedInfrastructureCount: z.number(),
  recentActivity: z.array(z.any())
});
export type Dashboard = z.infer<typeof DashboardSchema>;

export const PageSchema = <T extends z.ZodType>(itemSchema: T) => z.object({
  content: z.array(itemSchema),
  totalElements: z.number(),
  totalPages: z.number(),
  size: z.number(),
  number: z.number()
});

export const StudentSummarySchema = z.object({
  id: z.string(),
  studentNumber: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
  status: z.string()
});
export type StudentSummary = z.infer<typeof StudentSummarySchema>;

export const SshKeySchema = z.object({
  id: z.string(),
  label: z.string(),
  fingerprint: z.string(),
  createdAt: z.string()
});
export type SshKey = z.infer<typeof SshKeySchema>;

export const StudentDetailSchema = StudentSummarySchema.extend({
  sshKeys: z.array(SshKeySchema),
  progress: z.array(z.any())
});
export type StudentDetail = z.infer<typeof StudentDetailSchema>;

export const CourseSummarySchema = z.object({
  id: z.string(),
  courseKey: z.string(),
  name: z.string(),
  status: z.string()
});
export type CourseSummary = z.infer<typeof CourseSummarySchema>;

export const CourseDefinitionSchema = z.object({
  courseKey: z.string().min(1, 'Course key is required'),
  name: z.string().min(1, 'Name is required'),
  description: z.string().optional().nullable(),
  semester: z.string().optional().nullable(),
  startsOn: z.string().optional().nullable(),
  endsOn: z.string().optional().nullable(),
  timezone: z.string().min(1, 'Timezone is required'),
  status: z.string().min(1, 'Status is required'),
  registrationOpensAt: z.string().optional().nullable(),
  registrationClosesAt: z.string().optional().nullable(),
  registrationEnabled: z.boolean()
});
export type CourseDefinition = z.infer<typeof CourseDefinitionSchema>;

export const CourseViewSchema = CourseSummarySchema.extend({
  description: z.string().optional().nullable(),
  semester: z.string().optional().nullable(),
  startsOn: z.string().optional().nullable(),
  endsOn: z.string().optional().nullable(),
  timezone: z.string(),
  registrationOpensAt: z.string().optional().nullable(),
  registrationClosesAt: z.string().optional().nullable(),
  registrationEnabled: z.boolean()
});
export type CourseView = z.infer<typeof CourseViewSchema>;

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

export const AssignmentSummarySchema = z.object({
  id: z.string(),
  courseId: z.string(),
  assignmentKey: z.string(),
  title: z.string(),
  status: z.string(),
  dueAt: z.string().optional().nullable()
});
export type AssignmentSummary = z.infer<typeof AssignmentSummarySchema>;

export const AssignmentDefinitionSchema = z.object({
  courseId: z.string().min(1, 'Course ID is required'),
  assignmentKey: z.string().min(1, 'Assignment key is required'),
  title: z.string().min(1, 'Title is required'),
  description: z.string().optional().nullable(),
  displayOrder: z.number().int(),
  status: z.string().min(1, 'Status is required'),
  mandatory: z.boolean(),
  opensAt: z.string().nullable().optional(),
  dueAt: z.string().nullable().optional(),
  timezone: z.string().nullable().optional(),
  maxPoints: z.number().min(0),
  testCount: z.number().int().min(0),
  passThreshold: z.number().min(0),
  allowLate: z.boolean(),
  templateVersionId: z.string().nullable().optional(),
  testSuiteVersionId: z.string().nullable().optional(),
  runtimeId: z.string().nullable().optional(),
  timeoutSeconds: z.number().int().min(1).nullable().optional(),
  memoryLimitBytes: z.number().int().min(1).nullable().optional(),
  cpuLimit: z.number().min(0).nullable().optional(),
  pidLimit: z.number().int().min(1).nullable().optional(),
  networkEnabled: z.boolean()
});
export type AssignmentDefinition = z.infer<typeof AssignmentDefinitionSchema>;

export const AssignmentDetailSchema = AssignmentSummarySchema.extend({
  description: z.string().optional().nullable(),
  displayOrder: z.number(),
  mandatory: z.boolean(),
  opensAt: z.string().optional().nullable(),
  timezone: z.string(),
  maxPoints: z.number(),
  testCount: z.number(),
  passThreshold: z.number(),
  allowLate: z.boolean(),
  templateVersionId: z.string().optional().nullable(),
  testSuiteVersionId: z.string().optional().nullable(),
  runtimeId: z.string().optional().nullable(),
  timeoutSeconds: z.number().nullable().optional(),
  memoryLimitBytes: z.number().nullable().optional(),
  cpuLimit: z.number().nullable().optional(),
  pidLimit: z.number().nullable().optional(),
  networkEnabled: z.boolean()
});
export type AssignmentDetail = z.infer<typeof AssignmentDetailSchema>;

export const TemplateSchema = z.object({
  id: z.string(),
  templateKey: z.string(),
  name: z.string(),
  description: z.string().optional().nullable()
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
  publishedAt: z.string().optional().nullable(),
  publishedBy: z.string().optional().nullable()
});
export type TemplateVersion = z.infer<typeof TemplateVersionSchema>;

export const TestSuiteSchema = z.object({
  id: z.string(),
  suiteKey: z.string(),
  name: z.string(),
  description: z.string().optional().nullable()
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
  contentHash: z.string(),
  hiddenTestCount: z.number(),
  publicTestCount: z.number(),
  publishedAt: z.string().optional().nullable(),
  publishedBy: z.string().optional().nullable()
});
export type TestSuiteVersion = z.infer<typeof TestSuiteVersionSchema>;

export const SubmissionSummarySchema = z.object({
  id: z.string(),
  assignmentId: z.string(),
  studentId: z.string(),
  commitSha: z.string(),
  shortCommitSha: z.string(),
  commitMessage: z.string().optional().nullable(),
  status: z.string(),
  receivedAt: z.string()
});
export type SubmissionSummary = z.infer<typeof SubmissionSummarySchema>;

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
    submissionCount: z.number(),
    lastActivityAt: z.string().optional().nullable(),
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
  actorName: z.string().optional().nullable(),
  subjectType: z.string().optional().nullable(),
  subjectId: z.string().optional().nullable(),
  outcome: z.string().optional().nullable(),
  detail: z.record(z.string(), z.unknown()).optional().nullable()
});
export type AuditEvent = z.infer<typeof AuditEventSchema>;

export const RuntimeSchema = z.object({
  id: z.string(),
  runtimeKey: z.string(),
  displayName: z.string(),
  image: z.string(),
  tag: z.string(),
  imageDigest: z.string(),
  installCommand: z.string().optional().nullable(),
  testCommand: z.string(),
  reportFormat: z.string(),
  enabled: z.boolean()
});
export type Runtime = z.infer<typeof RuntimeSchema>;

export const RuntimeDefinitionSchema = z.object({
  runtimeKey: z.string().min(1, "Required"),
  displayName: z.string().min(1, "Required"),
  image: z.string().min(1, "Required"),
  tag: z.string().min(1, "Required").refine(val => val !== 'latest', { message: "tag 'latest' is not reproducible" }),
  imageDigest: z.string().regex(/^sha256:[a-f0-9]{64}$/, "Must be a valid sha256 digest"),
  installCommand: z.string().optional().nullable(),
  testCommand: z.string().min(1, "Required"),
  reportFormat: z.string().min(1, "Required"),
  enabled: z.boolean()
});
export type RuntimeDefinition = z.infer<typeof RuntimeDefinitionSchema>;

// API Calls
export const api = {
  getMeta: () => fetchApi<Meta>('/api/v1/meta'),
  getAvailability: () => fetchApi<Availability>('/api/v1/registration/availability'),
  register: (req: RegistrationRequest) => fetchApi<RegistrationResponse>('/api/v1/registration', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  getResult: (token: string) => fetchApi<PublicResult>(`/api/v1/results/${token}`),
  getMe: () => fetchApi<Me>('/api/v1/me'),
  // Spring Security's logout filter, configured in WebSecurityConfig, listens at
  // /logout rather than under /api. This previously pointed at /api/v1/auth/logout,
  // which no controller serves: the call failed and the session stayed alive while the
  // UI behaved as though the user had signed out.
  logout: () => fetchApi<undefined>('/logout', { method: 'POST' }),
  getDashboard: () => fetchApi<Dashboard>('/api/v1/dashboard'),
  getStudents: (params?: Record<string, string>) => {
    const qs = params ? new URLSearchParams(params).toString() : '';
    return fetchApi<z.infer<ReturnType<typeof PageSchema<typeof StudentSummarySchema>>>>(`/api/v1/students${qs ? '?' + qs : ''}`);
  },
  getStudent: (id: string) => fetchApi<StudentDetail>(`/api/v1/students/${id}`),
  updateStudentStatus: (id: string, req: { status: string; reason: string }) => fetchApi<undefined>(`/api/v1/students/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  getAssignments: (params?: Record<string, string>) => {
    const qs = params ? new URLSearchParams(params).toString() : '';
    return fetchApi<z.infer<ReturnType<typeof PageSchema<typeof AssignmentSummarySchema>>>>(`/api/v1/assignments${qs ? '?' + qs : ''}`);
  },
  getSubmissions: (params?: Record<string, string>) => {
    const qs = params ? new URLSearchParams(params).toString() : '';
    return fetchApi<z.infer<ReturnType<typeof PageSchema<typeof SubmissionSummarySchema>>>>(`/api/v1/submissions${qs ? '?' + qs : ''}`);
  },
  getCourseReport: (courseId: string) => fetchApi<CourseReport>(`/api/v1/reports/courses/${courseId}`),
  getCourses: (params?: Record<string, string>) => {
    const qs = params ? new URLSearchParams(params).toString() : '';
    return fetchApi<z.infer<ReturnType<typeof PageSchema<typeof CourseSummarySchema>>>>(`/api/v1/courses${qs ? '?' + qs : ''}`);
  },
  createCourse: (req: CourseDefinition) => fetchApi<undefined>('/api/v1/courses', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  updateCourse: (id: string, req: CourseDefinition) => fetchApi<CourseView>(`/api/v1/courses/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  getCourse: (id: string) => fetchApi<CourseView>(`/api/v1/courses/${id}`),
  getCourseClasses: (id: string) => fetchApi<Class[]>(`/api/v1/courses/${id}/classes`),
  createClass: (id: string, req: ClassDefinition) => fetchApi<undefined>(`/api/v1/courses/${id}/classes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  updateClass: (id: string, classId: string, req: ClassDefinition) => fetchApi<undefined>(`/api/v1/courses/${id}/classes/${classId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  createAssignment: (req: AssignmentDefinition) => fetchApi<undefined>('/api/v1/assignments', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  updateAssignment: (id: string, req: AssignmentDefinition) => fetchApi<AssignmentDetail>(`/api/v1/assignments/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  getAssignment: (id: string) => fetchApi<AssignmentDetail>(`/api/v1/assignments/${id}`),
  publishAssignment: (id: string) => fetchApi<undefined>(`/api/v1/assignments/${id}/publish`, { method: 'POST' }),
  
  getTemplates: (params?: Record<string, string>) => {
    const qs = params ? new URLSearchParams(params).toString() : '';
    return fetchApi<z.infer<ReturnType<typeof PageSchema<typeof TemplateSchema>>>>(`/api/v1/templates${qs ? '?' + qs : ''}`);
  },
  createTemplate: (req: TemplateDefinition) => fetchApi<undefined>('/api/v1/templates', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  getTemplateVersions: (id: string) => fetchApi<TemplateVersion[]>(`/api/v1/templates/${id}/versions`),
  createTemplateVersion: (id: string, formData: FormData) => postMultipart<undefined>(`/api/v1/templates/${id}/versions`, formData),
  publishTemplateVersion: (versionId: string) => fetchApi<undefined>(`/api/v1/templates/versions/${versionId}/publish`, { method: 'POST' }),

  getTestSuites: (params?: Record<string, string>) => {
    const qs = params ? new URLSearchParams(params).toString() : '';
    return fetchApi<z.infer<ReturnType<typeof PageSchema<typeof TestSuiteSchema>>>>(`/api/v1/test-suites${qs ? '?' + qs : ''}`);
  },
  createTestSuite: (req: TestSuiteDefinition) => fetchApi<undefined>('/api/v1/test-suites', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),
  getTestSuiteVersions: (id: string) => fetchApi<TestSuiteVersion[]>(`/api/v1/test-suites/${id}/versions`),
  createTestSuiteVersion: (id: string, formData: FormData) => postMultipart<undefined>(`/api/v1/test-suites/${id}/versions`, formData),
  publishTestSuiteVersion: (versionId: string, req: { hiddenTestCount: number; publicTestCount: number }) => fetchApi<{ hiddenTestCount: number; publicTestCount: number }>(`/api/v1/test-suites/versions/${versionId}/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  }),

  getAuditLog: (params?: Record<string, string>) => {
    const qs = new URLSearchParams({ sort: 'occurredAt,desc', ...params }).toString();
    return fetchApi<z.infer<ReturnType<typeof PageSchema<typeof AuditEventSchema>>>>(`/api/v1/audit?${qs}`);
  },
  getRuntimes: () => fetchApi<Runtime[]>('/api/v1/runtimes'),
  createRuntime: (req: RuntimeDefinition) => fetchApi<undefined>('/api/v1/runtimes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req)
  })
};
