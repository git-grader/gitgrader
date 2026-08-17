// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { CourseStatusChip } from '../components/CourseStatusChip';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import { fromZonedInputValue, toZonedInputValue } from '../components/localDateTime';
import type { ClassDefinition, CourseDefinition, CourseView, Class } from '../api';
import {
  Typography, CircularProgress, Paper, Button, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, Alert, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Box,
  FormControl, InputLabel, Select, MenuItem, FormControlLabel, Switch
} from '@mui/material';

const COURSE_STATUSES = ['DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED'] as const;

function EditCourseForm({ course, open, onClose }: { course: CourseView; open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Partial<CourseDefinition>>(() => ({
    ...course,
    registrationOpensAt: toZonedInputValue(course.registrationOpensAt, course.timezone),
    registrationClosesAt: toZonedInputValue(course.registrationClosesAt, course.timezone)
  }));

  const updateMutation = useMutation({
    mutationFn: (req: CourseDefinition) => api.updateCourse(course.id, req),
    // The updated course comes back in the response, so the detail view can be corrected
    // from it directly rather than shown stale until a refetch lands.
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.courses.detail(course.id), updated);
      void queryClient.invalidateQueries({ queryKey: queryKeys.courses.all });
      onClose();
    }
  });

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    const zone = form.timezone ?? course.timezone;
    updateMutation.mutate({
      courseKey: course.courseKey,
      name: form.name ?? '',
      description: form.description ?? null,
      semester: form.semester ?? null,
      startsOn: form.startsOn ?? null,
      endsOn: form.endsOn ?? null,
      timezone: zone,
      status: form.status ?? course.status,
      registrationOpensAt: fromZonedInputValue(form.registrationOpensAt, zone),
      registrationClosesAt: fromZonedInputValue(form.registrationClosesAt, zone),
      registrationEnabled: form.registrationEnabled ?? course.registrationEnabled
    });
  };

  const fieldErrors = problemFieldErrors(updateMutation.error);

  return (
    <Dialog open={open} onClose={() => !updateMutation.isPending && onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>Edit Course</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <MutationErrorAlert error={updateMutation.error} />
          <TextField
            label="Course Key"
            value={course.courseKey}
            disabled
            fullWidth
            helperText="The course key is read-only because student repository paths are built from it."
          />
          <TextField
            label="Name"
            required
            fullWidth
            value={form.name ?? ''}
            onChange={e => setForm({ ...form, name: e.target.value })}
            error={!!fieldErrors['name']}
            helperText={fieldErrors['name']}
            disabled={updateMutation.isPending}
          />
          <TextField
            label="Description"
            fullWidth
            value={form.description ?? ''}
            onChange={e => setForm({ ...form, description: e.target.value })}
            error={!!fieldErrors['description']}
            helperText={fieldErrors['description']}
            disabled={updateMutation.isPending}
          />
          <TextField
            label="Semester"
            fullWidth
            value={form.semester ?? ''}
            onChange={e => setForm({ ...form, semester: e.target.value })}
            disabled={updateMutation.isPending}
          />
          <FormControl fullWidth>
            <InputLabel id="status-label">Status</InputLabel>
            <Select
              labelId="status-label"
              value={form.status ?? 'DRAFT'}
              label="Status"
              onChange={(e) => setForm({ ...form, status: e.target.value })}
              disabled={updateMutation.isPending}
            >
              {COURSE_STATUSES.map(status => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </Select>
          </FormControl>
          <TextField
            label="Timezone"
            required
            fullWidth
            value={form.timezone ?? ''}
            onChange={e => setForm({ ...form, timezone: e.target.value })}
            helperText="The zone the registration window below is stated in."
            disabled={updateMutation.isPending}
          />
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Starts On"
              type="date"
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
              value={form.startsOn ?? ''}
              onChange={e => setForm({ ...form, startsOn: e.target.value })}
              disabled={updateMutation.isPending}
            />
            <TextField
              label="Ends On"
              type="date"
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
              value={form.endsOn ?? ''}
              onChange={e => setForm({ ...form, endsOn: e.target.value })}
              disabled={updateMutation.isPending}
            />
          </Box>
          <FormControlLabel
            control={
              <Switch
                checked={form.registrationEnabled ?? false}
                onChange={e => setForm({ ...form, registrationEnabled: e.target.checked })}
                disabled={updateMutation.isPending}
              />
            }
            label="Registration Enabled"
          />
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              label="Registration Opens At"
              type="datetime-local"
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
              value={form.registrationOpensAt ?? ''}
              onChange={e => setForm({ ...form, registrationOpensAt: e.target.value })}
              disabled={updateMutation.isPending}
            />
            <TextField
              label="Registration Closes At"
              type="datetime-local"
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
              value={form.registrationClosesAt ?? ''}
              onChange={e => setForm({ ...form, registrationClosesAt: e.target.value })}
              disabled={updateMutation.isPending}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={updateMutation.isPending}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={updateMutation.isPending}>
            {updateMutation.isPending ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

function EditClassForm({ courseId, cls, open, onClose }: { courseId: string; cls: Class | null; open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Partial<ClassDefinition>>(() => ({
    classKey: cls?.classKey ?? '',
    name: cls?.name ?? ''
  }));

  const mutationFn = (req: ClassDefinition) =>
    cls ? api.updateClass(courseId, cls.id, req) : api.createClass(courseId, req);

  const mutation = useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.courses.classes(courseId) });
      onClose();
    }
  });

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    mutation.mutate({
      classKey: cls ? cls.classKey : (form.classKey ?? ''),
      name: form.name ?? ''
    });
  };

  const fieldErrors = problemFieldErrors(mutation.error);

  return (
    <Dialog open={open} onClose={() => !mutation.isPending && onClose()} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit}>
        <DialogTitle>{cls ? 'Edit Class' : 'New Class'}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <MutationErrorAlert error={mutation.error} />
          <TextField
            label="Class Key"
            required
            fullWidth
            value={cls ? cls.classKey : (form.classKey ?? '')}
            onChange={e => !cls && setForm({ ...form, classKey: e.target.value })}
            error={!!fieldErrors['classKey']}
            helperText={cls ? 'The class key is read-only because student repository paths are built from it.' : fieldErrors['classKey']}
            disabled={!!cls || mutation.isPending}
          />
          <TextField
            label="Name"
            required
            fullWidth
            value={form.name ?? ''}
            onChange={e => setForm({ ...form, name: e.target.value })}
            error={!!fieldErrors['name']}
            helperText={fieldErrors['name']}
            disabled={mutation.isPending}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={mutation.isPending}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

export function CourseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const courseId = id ?? '';
  const [courseOpen, setCourseOpen] = useState(false);
  const [classOpen, setClassOpen] = useState(false);
  const [editingClass, setEditingClass] = useState<Class | null>(null);
  // Whether registration is open is a statement about right now, and computing it once
  // per render meant a page left open kept claiming a window that had since closed.
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(timer);
  }, []);

  const { data: course, isLoading: courseLoading, isError: courseFailed, refetch: refetchCourse } = useQuery({
    queryKey: queryKeys.courses.detail(courseId),
    queryFn: () => api.getCourse(courseId),
    enabled: courseId !== ''
  });

  const {
    data: classes,
    isLoading: classesLoading,
    isError: classesFailed,
    refetch: refetchClasses
  } = useQuery({
    queryKey: queryKeys.courses.classes(courseId),
    queryFn: () => api.getCourseClasses(courseId),
    enabled: courseId !== ''
  });

  if (courseLoading || classesLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress aria-label="Loading course" />
      </Box>
    );
  }

  // A failed request is not a missing course, and telling an instructor their course
  // does not exist is a far more alarming answer than the truth.
  if (courseFailed) {
    return <QueryErrorNotice message="The course could not be loaded." onRetry={() => void refetchCourse()} />;
  }

  if (!course) {
    return <QueryErrorNotice message="The course could not be loaded." onRetry={() => void refetchCourse()} />;
  }

  const registrationOpen =
    course.status === 'ACTIVE' &&
    course.registrationEnabled &&
    (!course.registrationOpensAt || new Date(course.registrationOpensAt) <= now) &&
    (!course.registrationClosesAt || new Date(course.registrationClosesAt) > now);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" component="h1">{course.name}</Typography>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              Key: {course.courseKey}
            </Typography>
            <CourseStatusChip status={course.status} />
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          <Button variant="outlined" onClick={() => setCourseOpen(true)}>
            Edit Course
          </Button>
          <Button variant="outlined" component={Link} to={`/assignments?courseId=${course.id}`}>
            View Assignments
          </Button>
          {/* The report existed but nothing linked to it, so it could only be opened by
              typing an address containing the course's identifier. */}
          <Button variant="outlined" component={Link} to={`/reports/course/${course.id}`}>
            View Report
          </Button>
        </Box>
      </Box>

      {!registrationOpen ? (
        <Alert severity="warning">
          {course.status !== 'ACTIVE'
            ? 'Students cannot register for this course because the course status is not ACTIVE. Use the Edit Course form to change it.'
            : !course.registrationEnabled
            ? 'Students cannot register for this course because registration is switched off. Use the Edit Course form to enable it.'
            : course.registrationOpensAt && new Date(course.registrationOpensAt) > now
            ? `Students cannot register for this course because registration does not open until ${new Date(course.registrationOpensAt).toLocaleString()}.`
            : course.registrationClosesAt && new Date(course.registrationClosesAt) <= now
            ? `Students cannot register for this course because registration closed at ${new Date(course.registrationClosesAt).toLocaleString()}. Use the Edit Course form to extend it.`
            : 'Students cannot register for this course.'}
        </Alert>
      ) : (
        <Alert severity="success">
          The course is currently accepting student registrations.
        </Alert>
      )}

      <Paper sx={{ p: 0 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: 2 }}>
          <Typography variant="h6">Classes</Typography>
          <Button variant="contained" size="small" onClick={() => {
            setEditingClass(null);
            setClassOpen(true);
          }}>Add Class</Button>
        </Box>

        {/* A failed request left `classes` undefined, which is neither an empty list nor
            an error as far as the table was concerned: it rendered no rows and no
            message, so a course whose classes could not be loaded looked like a course
            with none. */}
        {classesFailed ? (
          <QueryErrorNotice
            message="The classes for this course could not be loaded."
            onRetry={() => void refetchClasses()}
          />
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Class Key</TableCell>
                  <TableCell>Name</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {!classes || classes.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={3} align="center" sx={{ py: 4, color: 'text.secondary' }}>No classes found.</TableCell>
                  </TableRow>
                ) : (
                  classes.map(cls => (
                    <TableRow key={cls.id}>
                      <TableCell>{cls.classKey}</TableCell>
                      <TableCell>{cls.name}</TableCell>
                      <TableCell align="right">
                        <Button size="small" onClick={() => {
                          setEditingClass(cls);
                          setClassOpen(true);
                        }}>Edit</Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {courseOpen && (
        <EditCourseForm
          key={course.id}
          course={course}
          open={courseOpen}
          onClose={() => setCourseOpen(false)}
        />
      )}

      {classOpen && (
        <EditClassForm
          key={editingClass ? editingClass.id : 'new'}
          courseId={course.id}
          cls={editingClass}
          open={classOpen}
          onClose={() => {
            setClassOpen(false);
            setEditingClass(null);
          }}
        />
      )}
    </Box>
  );
}
