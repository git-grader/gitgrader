// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { StrictMode, useMemo } from 'react';
import { createBrowserRouter, RouterProvider, useRouteError, isRouteErrorResponse, Navigate, Link } from 'react-router';
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiProblem } from './api/client';
import { CssBaseline, ThemeProvider, useMediaQuery, Box, Button, Typography } from '@mui/material';
import { createAppTheme } from './theme';
import PrimaryLogo from './assets/brand/gitgrader-lockup-primary.svg';
import './styles/fonts.css';
import { MetaProvider } from './components/MetaProvider';
import { RegistrationPage } from './pages/RegistrationPage';
import { RegistrationSuccessPage } from './pages/RegistrationSuccessPage';
import { PublicResultPage } from './pages/PublicResultPage';
import { DashboardPage } from './pages/DashboardPage';
import { StudentsPage } from './pages/StudentsPage';
import { ReportPage } from './pages/ReportPage';
import { LoginPage } from './pages/LoginPage';
import { InstructorLayout } from './components/InstructorLayout';
import { RequireAdmin } from './components/RequireAdmin';
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
import { MaterialsPage } from './pages/MaterialsPage';

/**
 * Sends an expired session back to the sign-in page.
 *
 * A full navigation rather than a router push, because it is also what discards the
 * cache: every page holds the previous user's data until the document is replaced, and
 * that data includes student records and audit entries.
 */
function redirectToSignIn() {
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
}

const queryClient = new QueryClient({
  // A 401 is a session problem, not a page problem, so it is handled once here. Left to
  // the pages, each one renders its own `!data` branch and an expired session looks like
  // a blank screen rather than a prompt to sign in again.
  queryCache: new QueryCache({
    onError: (error) => {
      if (error instanceof ApiProblem && error.status === 401) {
        redirectToSignIn();
      }
    }
  }),
  defaultOptions: {
    queries: {
      // A 4xx is an answer, not a hiccup; retrying one only delays it.
      retry: (failureCount, error) => {
        if (error instanceof ApiProblem && error.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount < 3;
      }
    }
  }
});

/**
 * Describes whatever was thrown, without assuming it was an Error.
 *
 * `useRouteError` is typed `unknown` because anything can be thrown, and casting it to
 * `Error` to read `.message` rendered the word "undefined" for a thrown string and threw
 * again inside the error page for a thrown null.
 */
function errorMessage(error: unknown): string {
  if (isRouteErrorResponse(error)) return error.statusText;
  if (error instanceof Error) return error.message;
  if (typeof error === 'string' && error) return error;
  return 'Something went wrong.';
}

function ErrorBoundary() {
  const error = useRouteError();
  return (
    <Box role="alert" sx={{ p: 4 }}>
      <Typography variant="h4" gutterBottom>Oops!</Typography>
      <Typography color="error">{errorMessage(error)}</Typography>
    </Box>
  );
}

/**
 * Shown for an address that matches no route.
 *
 * Carries the product's own chrome and a way back. The bare heading it replaced gave a
 * visitor nothing to act on and, with no logo or navigation, read as a broken server
 * rather than a mistyped address. It offers sign-in rather than the dashboard because a
 * student who mistyped a result link has no dashboard: that button sent them to a page
 * that bounced them straight back to sign-in.
 */
function NotFound() {
  return (
    <Box
      role="alert"
      sx={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        gap: 2,
        p: 4
      }}
    >
      <Box component="img" src={PrimaryLogo} alt="GitGrader" sx={{ height: 40, mb: 1 }} />
      <Typography variant="h4" component="h1">Page not found</Typography>
      <Typography color="text.secondary" sx={{ maxWidth: 420 }}>
        The address you opened does not exist. If you followed a result link, it may have
        been changed or revoked since it was sent to you - ask your instructor for a new
        one. If you teach here, sign in to continue.
      </Typography>
      <Button component={Link} to="/login" variant="contained" sx={{ mt: 1 }}>
        Go to sign-in
      </Button>
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
          { path: 'materials', element: <MaterialsPage /> },
          { path: 'submissions', element: <SubmissionsPage /> },
          { path: 'submissions/:id', element: <SubmissionDetailPage /> },
          { path: 'reports/course/:courseId', element: <ReportPage /> },
          {
            path: 'admin',
            element: <RequireAdmin />,
            children: [
              { path: 'audit', element: <AdminAuditPage /> },
              { path: 'settings', element: <AdminSettingsPage /> },
              { path: 'runtimes', element: <AdminRuntimesPage /> }
            ]
          }
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
  const theme = useMemo(() => createAppTheme(prefersDarkMode ? 'dark' : 'light'), [prefersDarkMode]);

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
