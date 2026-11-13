// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useLocation, Navigate } from 'react-router';
import { Alert, Box, Typography, Paper } from '@mui/material';
import { RegistrationResponseSchema } from '../api';
import { useMeta } from '../components/MetaProvider';
import { BrandMark } from '../components/BrandMark';
import { cloneCommand } from '../components/cloneUrl';
import { CopyableCode } from '../components/CopyableCode';

export function RegistrationSuccessPage() {
  const meta = useMeta();
  const location = useLocation();
  // Routing state is whatever the previous page put there, and casting it meant a
  // malformed value crashed on the first field this page read rather than being
  // recognised as not a registration at all.
  const state: unknown = location.state;
  const parsed = RegistrationResponseSchema.safeParse(
    state && typeof state === 'object' && 'result' in state ? state.result : undefined
  );

  if (!parsed.success) return <Navigate to="/register" replace />;
  const result = parsed.data;
  const courseKey =
    state && typeof state === 'object' && 'courseKey' in state && typeof state.courseKey === 'string'
      ? state.courseKey
      : null;
  const command = cloneCommand(
    meta,
    `${courseKey ?? '<course-key>'}/<assignment-key>/${result.studentUsername}`
  );

  return (
    <Box component="main" sx={{ minHeight: '100vh', p: { xs: 2, sm: 4 }, maxWidth: 760, width: '100%', mx: 'auto', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1, bgcolor: 'background.default' }}>
      <BrandMark />
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 4 }, width: '100%' }}>
        <Typography variant="h4" component="h1" sx={{ overflowWrap: 'anywhere', mb: 2 }}>Registration Successful</Typography>
        <Typography component="p" sx={{ mb: 1, overflowWrap: 'anywhere' }}>
          Welcome, {result.fullName}. Your student username is {result.studentUsername} and your SSH key fingerprint is {result.keyFingerprint}.
        </Typography>
        {/* May a self-registration push yet? That is decided by the deployment's
            registration policy, not by the status alone, so the copy has to follow the
            flag rather than assert what this status does unconditionally. The identity is
            the email and student username collected at registration: self-registration
            never confirms identity, so nothing here promises a later verification. */}
        {result.status === 'SELF_REGISTERED' && (
          <Alert severity="info" sx={{ mb: 2 }}>
            {meta.requireInstructorVerification
              ? 'Your registration is recorded but not yet verified by an instructor. You can clone now; pushes are accepted once an instructor verifies your registration.'
              : 'Your registration is recorded. Your email and student username identify your work, and you can clone and push now.'}
          </Alert>
        )}
        <Typography variant="h6" gutterBottom>Cloning your work</Typography>
        {/* The repositories are created from the registration event, so they do not exist
            yet when this page renders. Listing them here meant an empty list under a
            heading promising assignments; the address they will appear at is knowable
            now, and is what the student actually needs. */}
        <Typography component="p" sx={{ mb: 1, overflowWrap: 'anywhere' }}>
          One repository is being prepared for each assignment. Clone the one you have been set with:
        </Typography>
        <CopyableCode text={command} />
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Your instructor publishes the assignment key for each piece of work.
        </Typography>
      </Paper>
    </Box>
  );
}
