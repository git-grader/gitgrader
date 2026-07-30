// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { z } from 'zod';
import { fetchApi } from './client';

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
  email: z.string().email('Invalid email'),
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

export const PublicResultSchema = z.object({
  assignmentTitle: z.string(),
  courseName: z.string(),
  commitSha: z.string(),
  receivedAt: z.string(),
  verified: z.boolean(),
  passed: z.number(),
  total: z.number(),
  score: z.number(),
  tests: z.array(z.object({
    public: z.boolean(),
    name: z.string().optional(),
    category: z.string().optional(),
    outcome: z.string(),
    message: z.string().optional(),
    hint: z.string().optional()
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

export const PageSchema = <T extends z.ZodTypeAny>(itemSchema: T) => z.object({
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

export const AssignmentSummarySchema = z.object({
  id: z.string(),
  courseId: z.string(),
  assignmentKey: z.string(),
  title: z.string(),
  status: z.string(),
  dueAt: z.string().optional().nullable()
});
export type AssignmentSummary = z.infer<typeof AssignmentSummarySchema>;

export const SubmissionSummarySchema = z.object({
  id: z.string(),
  assignmentId: z.string(),
  studentId: z.string(),
  commitSha: z.string(),
  status: z.string(),
  score: z.number().optional().nullable(),
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
  eventType: z.string(),
  actorType: z.string(),
  timestamp: z.string()
});
export type AuditEvent = z.infer<typeof AuditEventSchema>;

export const RuntimeSchema = z.object({
  id: z.string(),
  name: z.string(),
  image: z.string()
});
export type Runtime = z.infer<typeof RuntimeSchema>;

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
  logout: () => fetchApi<undefined>('/api/v1/auth/logout', { method: 'POST' }),
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
  getCourses: () => fetchApi<z.infer<ReturnType<typeof PageSchema<typeof CourseSummarySchema>>>>('/api/v1/courses'),
  getAuditLog: () => fetchApi<z.infer<ReturnType<typeof PageSchema<typeof AuditEventSchema>>>>('/api/v1/audit'),
  getRuntimes: () => fetchApi<Runtime[]>('/api/v1/runtimes')
};
