// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api } from '../api';
import type { Class, ClassProgressReport, CourseReport, CourseView } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { PageHeader } from '../components/PageHeader';
import { Alert, Box, Typography, Grid, Paper, CircularProgress, Button, Stack } from '@mui/material';

interface ClassSummary {
  readonly courseClass: Class;
  readonly report: ClassProgressReport;
}

interface CourseSummary {
  readonly course: CourseView;
  readonly report: CourseReport;
  readonly classes: readonly ClassSummary[];
}

async function loadCourseSummaries(): Promise<CourseSummary[]> {
  const pageSize = '100';
  const firstPage = await api.getCourses({ status: 'ACTIVE', page: '0', size: pageSize });
  const remainingPages = await Promise.all(Array.from(
    { length: Math.max(0, firstPage.totalPages - 1) },
    (_, index) => api.getCourses({ status: 'ACTIVE', page: String(index + 1), size: pageSize })
  ));
  const courses = [firstPage, ...remainingPages].flatMap((page) => page.content);
  return Promise.all(courses.map(async (course) => {
    const [report, classes] = await Promise.all([api.getCourseReport(course.id), api.getCourseClasses(course.id)]);
    const classSummaries = await Promise.all(classes.map(async (courseClass) => ({
      courseClass,
      report: await api.getClassReport(course.id, courseClass.id)
    })));
    return { course, report, classes: classSummaries };
  }));
}

function completionPercent(values: readonly number[]): string {
  if (values.length === 0) return '0%';
  return `${Math.round(values.reduce((total, value) => total + value, 0) / values.length * 100)}%`;
}

export function DashboardPage() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: api.getDashboard
  });
  const coursesQuery = useQuery({
    queryKey: queryKeys.dashboardCourses,
    queryFn: loadCourseSummaries
  });

  if (isLoading) return <Box sx={{ p: 4 }}><CircularProgress aria-label="Loading dashboard" /></Box>;
  if (isError || !data) {
    return <QueryErrorNotice message="The dashboard could not be loaded." onRetry={() => void refetch()} />;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <PageHeader title="Dashboard" />

      {/* The count was fetched and then dropped, so the one number that says grading
          itself is broken - as opposed to students failing - was never shown anywhere. */}
      {data.failedInfrastructureCount > 0 && (
        <Alert severity="warning">
          {data.failedInfrastructureCount} grading {data.failedInfrastructureCount === 1 ? 'run' : 'runs'} could not be
          carried out. This is a platform fault rather than a student one, and the affected submissions can be graded
          again.
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper variant="outlined" sx={{ p: 2.5, minHeight: 112, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <Typography variant="h4" sx={{ fontVariantNumeric: 'tabular-nums' }}>{data.courseCount}</Typography>
            <Typography variant="body2" color="text.secondary">Courses</Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper variant="outlined" sx={{ p: 2.5, minHeight: 112, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <Typography variant="h4" sx={{ fontVariantNumeric: 'tabular-nums' }}>{data.studentCount}</Typography>
            <Typography variant="body2" color="text.secondary">Students</Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper variant="outlined" sx={{ p: 2.5, minHeight: 112, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <Typography variant="h4" sx={{ fontVariantNumeric: 'tabular-nums' }}>{data.openAssignmentCount}</Typography>
            <Typography variant="body2" color="text.secondary">Open assignments</Typography>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper variant="outlined" sx={{ p: 2.5, minHeight: 112, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <Typography variant="h4" sx={{ fontVariantNumeric: 'tabular-nums' }}>{data.runningGradingCount}</Typography>
            <Typography variant="body2" color="text.secondary">Running grading</Typography>
          </Paper>
        </Grid>
      </Grid>

      <Box component="section" aria-labelledby="available-courses-heading">
        <Typography id="available-courses-heading" variant="h5" component="h2" gutterBottom>
          Courses available
        </Typography>
        {coursesQuery.isLoading ? (
          <CircularProgress aria-label="Loading course summaries" />
        ) : coursesQuery.isError ? (
          <QueryErrorNotice
            message="Available course summaries could not be loaded."
            onRetry={() => void coursesQuery.refetch()}
          />
        ) : coursesQuery.data === undefined ? (
          <CircularProgress aria-label="Loading course summaries" />
        ) : coursesQuery.data.length === 0 ? (
          <Typography color="text.secondary">No active courses are available.</Typography>
        ) : (
          <Stack spacing={2}>
            {coursesQuery.data.map(({ course, report, classes }) => {
              const studentCount = report.students.length;
              const submissionCount = report.students.reduce((count, student) => count + student.submissionCount, 0);
              const courseCompletion = completionPercent(report.students.map((student) => student.completionRate));
              return (
                <Paper key={course.id} component="article" variant="outlined" sx={{ p: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
                    <Button component={Link} to={`/courses/${encodeURIComponent(course.id)}`} sx={{ p: 0, minWidth: 0 }}>
                      <Typography variant="h6" component="span">{course.name}</Typography>
                    </Button>
                    <Typography variant="body2" color="text.secondary">{course.semester ?? course.courseKey}</Typography>
                  </Box>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} useFlexGap sx={{ mt: 1, flexWrap: 'wrap' }}>
                    <Typography variant="body2">{studentCount} {studentCount === 1 ? 'student' : 'students'}</Typography>
                    <Typography variant="body2">{submissionCount} {submissionCount === 1 ? 'submission' : 'submissions'}</Typography>
                    <Typography variant="body2">{courseCompletion} average completion</Typography>
                    <Typography variant="body2">{classes.length} {classes.length === 1 ? 'class' : 'classes'}</Typography>
                  </Stack>
                  <Stack spacing={0.5} sx={{ mt: 1 }}>
                    {classes.length === 0 ? (
                      <Typography variant="body2" color="text.secondary">No classes in this course.</Typography>
                    ) : classes.map(({ courseClass, report: classReport }) => {
                      const classCount = classReport.students.length;
                      const classCompletion = completionPercent(classReport.students.map((student) => student.completionRate));
                      return (
                        <Button
                          key={courseClass.id}
                          component={Link}
                          to={`/courses/${encodeURIComponent(course.id)}/classes/${encodeURIComponent(courseClass.id)}`}
                          sx={{ alignSelf: 'flex-start', justifyContent: 'flex-start', px: 0 }}
                        >
                          {courseClass.name} · {classCount} {classCount === 1 ? 'student' : 'students'} · {classCompletion} completion
                        </Button>
                      );
                    })}
                  </Stack>
                </Paper>
              );
            })}
          </Stack>
        )}
      </Box>
    </Box>
  );
}
