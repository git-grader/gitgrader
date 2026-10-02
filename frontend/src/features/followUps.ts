// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import type { ClassProgressReport } from '../api';
export function classFollowUps(report: ClassProgressReport) {
  const students = report.students.filter(student => student.enrollmentStatus === 'ACTIVE');
  return {
    missing: students.filter(student => student.notStarted > 0).length,
    failed: students.filter(student => Object.values(student.assignments).some(result => result.latestSubmission?.status === 'FAILED')).length,
    infrastructure: students.filter(student => Object.values(student.assignments).some(result => result.latestSubmission?.status === 'INFRASTRUCTURE_ERROR')).length
  };
}
