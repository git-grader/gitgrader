// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItem, ListItemText, Typography } from '@mui/material';
import { api } from '../api';
import type { StudentSummary } from '../api';
import { queryKeys } from '../api/queryKeys';

interface Result { student: StudentSummary; outcome: 'verified' | 'failed' | 'skipped'; message: string }

export function BulkVerification({ students, disabled, onFinished, onBusyChange }: {
  students: StudentSummary[]; disabled: boolean; onFinished: () => void; onBusyChange: (busy: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [review, setReview] = useState<StudentSummary[] | null>(null);
  const [results, setResults] = useState<Result[]>([]);
  const [busy, setBusy] = useState(false);
  async function verify(targets: StudentSummary[]) {
    if (busy) return;
    setBusy(true); onBusyChange(true);
    // Keep successes when retrying failures; never submit those students again.
    let next = results.filter(result => !targets.some(student => student.id === result.student.id));
    setResults(next);
    try {
      for (const student of targets) {
        let result: Result;
        try {
          const current = await api.getStudent(student.id);
          if (current.student.status !== 'SELF_REGISTERED') {
            result = { student, outcome: 'skipped', message: `Skipped: current status is ${current.student.status}.` };
          } else {
            await api.verifyStudent(student.id);
            result = { student, outcome: 'verified', message: 'Verified.' };
          }
        } catch {
          result = { student, outcome: 'failed', message: 'Verification could not be completed. Retry after checking the connection or permissions.' };
        }
        next = [...next, result]; setResults(next);
      }
    } finally {
      void queryClient.invalidateQueries({ queryKey: ['students'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
      void queryClient.invalidateQueries({ queryKey: ['report'] });
      setBusy(false); onBusyChange(false); onFinished();
    }
  }
  const failures = results.filter(result => result.outcome === 'failed').map(result => result.student);
  return <>
    <Button variant="outlined" disabled={disabled || busy || students.length === 0} onClick={() => { setReview([...students]); setResults([]); }}>
      Verify selected ({students.length})
    </Button>
    <Dialog open={review !== null} onClose={() => { if (!busy) setReview(null); }} fullWidth maxWidth="sm">
      <DialogTitle>Review student verification</DialogTitle>
      <DialogContent>
        <Typography>Verify these {review?.length ?? 0} registrations? Each student is checked and verified individually.</Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>A failure leaves the other results intact. Registrations whose status has changed are skipped.</Typography>
        <List>{review?.map(student => <ListItem key={student.id}><ListItemText primary={`${student.firstName} ${student.lastName}`} secondary={student.studentUsername} /></ListItem>)}</List>
        {results.length > 0 && <Alert severity={failures.length ? 'warning' : 'info'} role="status">
          {results.filter(result => result.outcome === 'verified').length} verified · {failures.length} failed · {results.filter(result => result.outcome === 'skipped').length} skipped
        </Alert>}
        <List aria-label="Verification results">{results.map(result => <ListItem key={result.student.id}><ListItemText primary={result.student.studentUsername} secondary={result.message} /></ListItem>)}</List>
        {busy && <Typography role="status">Verifying registrations… {results.length} of {review?.length ?? 0} completed</Typography>}
      </DialogContent>
      <DialogActions>
        <Button disabled={busy} onClick={() => setReview(null)}>{results.length ? 'Close' : 'Cancel'}</Button>
        {!results.length && <Button variant="contained" disabled={busy || !review?.length} onClick={() => { if (review) void verify(review); }}>Confirm verification</Button>}
        {!!failures.length && <Button disabled={busy} onClick={() => void verify(failures)}>Retry failed only</Button>}
      </DialogActions>
    </Dialog>
  </>;
}
