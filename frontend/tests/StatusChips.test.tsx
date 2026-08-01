// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SubmissionStatusChip } from '../src/components/SubmissionStatusChip';
import { StudentStatusChip } from '../src/components/StudentStatusChip';
import { AssignmentStatusChip } from '../src/components/AssignmentStatusChip';
import { CourseStatusChip } from '../src/components/CourseStatusChip';

/**
 * These lists mirror the server enums. A value added there without a presentation here
 * falls through to the raw-value branch, which is the regression these tests exist to
 * catch: the grids previously rendered every status as an unreadable machine constant.
 */
const SUBMISSION_STATUSES = [
  'RECEIVED',
  'QUEUED',
  'RUNNING',
  'PASSED',
  'FAILED',
  'INFRASTRUCTURE_ERROR',
  'CANCELLED',
  'REJECTED'
];
const STUDENT_STATUSES = ['SELF_REGISTERED', 'VERIFIED_BY_INSTRUCTOR', 'SUSPENDED', 'ARCHIVED'];
const ASSIGNMENT_STATUSES = ['DRAFT', 'SCHEDULED', 'OPEN', 'CLOSED', 'ARCHIVED'];
const COURSE_STATUSES = ['DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED'];

describe('status chips', () => {
  it.each(SUBMISSION_STATUSES)('gives %s a human label', (status) => {
    render(<SubmissionStatusChip status={status} />);
    expect(screen.queryByText(status)).toBeNull();
  });

  it.each(STUDENT_STATUSES)('gives student status %s a human label', (status) => {
    render(<StudentStatusChip status={status} />);
    expect(screen.queryByText(status)).toBeNull();
  });

  it.each(ASSIGNMENT_STATUSES)('gives assignment status %s a human label', (status) => {
    render(<AssignmentStatusChip status={status} />);
    expect(screen.queryByText(status)).toBeNull();
  });

  it.each(COURSE_STATUSES)('gives course status %s a human label', (status) => {
    render(<CourseStatusChip status={status} />);
    expect(screen.queryByText(status)).toBeNull();
  });

  it('shows an unrecognised course status verbatim', () => {
    render(<CourseStatusChip status="SOME_FUTURE_STATUS" />);
    expect(screen.getByText('SOME_FUTURE_STATUS')).toBeInTheDocument();
  });

  it('does not claim a cancelled submission was superseded', () => {
    // The server also cancels a submission when a queue ceiling refuses it, so naming
    // only the supersede case would state the wrong reason to that student.
    render(<SubmissionStatusChip status="CANCELLED" />);
    expect(screen.getByText('Cancelled')).toBeInTheDocument();
    expect(screen.queryByText('Superseded')).toBeNull();
  });

  it('keeps a platform error distinct from a failed grade', () => {
    render(<SubmissionStatusChip status="INFRASTRUCTURE_ERROR" />);
    expect(screen.getByText('Platform error')).toBeInTheDocument();
  });

  it('shows an unrecognised status verbatim rather than dropping it', () => {
    render(<SubmissionStatusChip status="SOME_FUTURE_STATUS" />);
    expect(screen.getByText('SOME_FUTURE_STATUS')).toBeInTheDocument();
  });
});
