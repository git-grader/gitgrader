// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import type { AssignmentDetail, CourseView } from '../api';

export function deadlineLabel(iso: string, zone: string | null | undefined): string {
  const date = new Date(iso);
  if (!Number.isFinite(date.getTime())) return 'Invalid deadline';
  try { return `${new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone: zone || undefined }).format(date)} (${zone || Intl.DateTimeFormat().resolvedOptions().timeZone})`; }
  catch { return `${date.toISOString()} (UTC)`; }
}
const escapeText = (value: string) => value.replace(/\\/g, '\\\\').replace(/\r\n|\r|\n/g, '\\n').replace(/;/g, '\\;').replace(/,/g, '\\,');
const timestamp = (date: Date) => date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
// RFC 5545 limits physical lines to 75 octets. Fold without splitting UTF-8 characters.
export function foldCalendarLine(line: string): string {
  const encoder = new TextEncoder();
  let result = ''; let physical = ''; let bytes = 0;
  for (const character of line) {
    const length = encoder.encode(character).length;
    if (bytes + length > 75) { result += `${physical}\r\n`; physical = ' '; bytes = 1; }
    physical += character; bytes += length;
  }
  return result + physical;
}
export function deadlineCalendar(assignments: readonly AssignmentDetail[], courses: readonly CourseView[], origin: string, now = new Date()): string {
  const base = new URL(origin);
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//GitGrader//Assignment deadlines//EN', 'CALSCALE:GREGORIAN'];
  for (const assignment of assignments) {
    if (!assignment.dueAt || !Number.isFinite(new Date(assignment.dueAt).getTime())) continue;
    const course = courses.find(item => item.id === assignment.courseId);
    const zone = assignment.timezone || course?.timezone;
    lines.push('BEGIN:VEVENT', `UID:${encodeURIComponent(assignment.id)}@${base.hostname}`, `DTSTAMP:${timestamp(now)}`,
      `DTSTART:${timestamp(new Date(assignment.dueAt))}`,
      `SUMMARY:${escapeText(`${course?.name ?? 'Course'}: ${assignment.title} deadline`)}`,
      `DESCRIPTION:${escapeText(`Assignment deadline: ${deadlineLabel(assignment.dueAt, zone)}. Individual extensions are not included.`)}`,
      `URL:${new URL(`/assignments/${encodeURIComponent(assignment.id)}`, base).href}`, 'END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  return lines.map(foldCalendarLine).join('\r\n') + '\r\n';
}
export function downloadCalendar(content: string) {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/calendar;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url; link.download = 'gitgrader-deadlines.ics';
  document.body.append(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 0);
}
