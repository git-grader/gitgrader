// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Alert, Button, Chip, List, ListItem, ListItemText, Paper, Typography } from '@mui/material';
import type { AssignmentDetail } from '../api';
import type { useAssignmentMaterials } from '../hooks/useAssignmentMaterials';

type Materials = ReturnType<typeof useAssignmentMaterials>;
export function assignmentReadiness(assignment: AssignmentDetail, materials: Materials) {
  const runtime = materials.runtimes.find(item => item.id === assignment.runtimeId);
  return [
    { label: 'Published template', ready: materials.publishedTemplateVersions.some(item => item.id === assignment.templateVersionId), detail: 'Choose a published template version.' },
    { label: 'Published test suite', ready: materials.publishedSuiteVersions.some(item => item.id === assignment.testSuiteVersionId), detail: 'Choose a published test-suite version.' },
    { label: 'Enabled runtime', ready: !!runtime?.enabled, detail: 'Choose an enabled runtime.' },
    { label: 'Opening and deadline', ready: !!assignment.opensAt && !!assignment.dueAt && new Date(assignment.opensAt) < new Date(assignment.dueAt), detail: 'Set a deadline after the opening date.' },
    { label: 'Grading settings', ready: Number.isFinite(assignment.maxPoints) && assignment.maxPoints >= 0 && Number.isInteger(assignment.testCount) && assignment.testCount > 0 && assignment.passThreshold >= 0 && assignment.passThreshold <= 100, detail: 'Review points, a positive test count and the percentage threshold (0–100).' }
  ];
}
export function AssignmentReadiness({ assignment, materials }: { assignment: AssignmentDetail; materials: Materials }) {
  const checks = assignmentReadiness(assignment, materials);
  return <Paper component="section" aria-labelledby="readiness-heading" variant="outlined" sx={{ p: 3 }}>
    <Typography component="h2" variant="h6" id="readiness-heading">Publication readiness</Typography>
    <Typography variant="body2" color="text.secondary">Checklist for the saved configuration. Save your changes before reviewing or publishing.</Typography>
    <List>{checks.map(check => <ListItem key={check.label} sx={{ gap: 2, flexWrap: 'wrap' }}>
      <Chip size="small" color={check.ready ? 'success' : 'warning'} label={check.ready ? 'Ready' : 'Review'} />
      <ListItemText primary={check.label} secondary={check.ready ? 'Configured' : check.detail} />
      {!check.ready && assignment.status === 'DRAFT' && <Button href="#assignment-configuration">Review configuration</Button>}
    </ListItem>)}</List>
    <Alert severity={checks.every(check => check.ready) ? 'success' : 'info'}>
      {checks.every(check => check.ready) ? 'All checklist items are ready.' : 'Review the items above before publishing. The server makes the final publication decision.'}
    </Alert>
  </Paper>;
}
