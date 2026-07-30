// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { StrictMode, useMemo } from 'react';
import { createBrowserRouter, RouterProvider, useRouteError, isRouteErrorResponse, Navigate } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CssBaseline, ThemeProvider, createTheme, useMediaQuery, Box, Typography } from '@mui/material';
import { MetaProvider } from './components/MetaProvider';
import { RegistrationPage } from './pages/RegistrationPage';
import { RegistrationSuccessPage } from './pages/RegistrationSuccessPage';
import { PublicResultPage } from './pages/PublicResultPage';
import { DashboardPage } from './pages/DashboardPage';
import { StudentsPage } from './pages/StudentsPage';
import { ReportPage } from './pages/ReportPage';
import { LoginPage } from './pages/LoginPage';
import { InstructorLayout } from './components/InstructorLayout';
import { CoursesPage } from './pages/CoursesPage';
import { AssignmentsPage } from './pages/AssignmentsPage';
import { SubmissionsPage } from './pages/SubmissionsPage';
import { AdminAuditPage } from './pages/AdminAuditPage';
import { AdminSettingsPage } from './pages/AdminSettingsPage';
import { AdminRuntimesPage } from './pages/AdminRuntimesPage';
import { StudentDetailPage } from './pages/StudentDetailPage';
import { CourseDetailPage } from './pages/CourseDetailPage';
import { AssignmentDetailPage } from './pages/AssignmentDetailPage';
import { SubmissionDetailPage } from './pages/SubmissionDetailPage';

const queryClient = new QueryClient();

function ErrorBoundary() {
  const error = useRouteError();
  return (
    <Box p={4} role="alert">
      <Typography variant="h4" gutterBottom>Oops!</Typography>
      <Typography color="error">
        {isRouteErrorResponse(error) ? error.statusText : (error as Error).message}
      </Typography>
    </Box>
  );
}

function NotFound() {
  return (
    <Box p={4} role="alert">
      <Typography variant="h4" gutterBottom>404 Not Found</Typography>
    </Box>
  );
}

const router = createBrowserRouter([
  {
    path: '/',
    errorElement: <ErrorBoundary />,
    children: [
      {
        path: 'register',
        children: [
          { index: true, element: <RegistrationPage /> },
          { path: 'success', element: <RegistrationSuccessPage /> }
        ]
      },
      {
        path: 'result/:token',
        element: <PublicResultPage />
      },
      {
        path: 'login',
        element: <LoginPage />
      },
      {
        path: '',
        element: <InstructorLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'students', element: <StudentsPage /> },
          { path: 'students/:id', element: <StudentDetailPage /> },
          { path: 'courses', element: <CoursesPage /> },
          { path: 'courses/:id', element: <CourseDetailPage /> },
          { path: 'assignments', element: <AssignmentsPage /> },
          { path: 'assignments/:id', element: <AssignmentDetailPage /> },
          { path: 'submissions', element: <SubmissionsPage /> },
          { path: 'submissions/:id', element: <SubmissionDetailPage /> },
          { path: 'reports/course/:courseId', element: <ReportPage /> },
          { path: 'admin/audit', element: <AdminAuditPage /> },
          { path: 'admin/settings', element: <AdminSettingsPage /> },
          { path: 'admin/runtimes', element: <AdminRuntimesPage /> }
        ]
      },
      {
        path: '*',
        element: <NotFound />
      }
    ]
  }
]);

export function App() {
  const prefersDarkMode = useMediaQuery('(prefers-color-scheme: dark)');
  const theme = useMemo(() => createTheme({
    palette: {
      mode: prefersDarkMode ? 'dark' : 'light',
    },
  }), [prefersDarkMode]);

  return (
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <MetaProvider>
            <RouterProvider router={router} />
          </MetaProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </StrictMode>
  );
}
