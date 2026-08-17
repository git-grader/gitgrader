// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert } from '../components/MutationErrorAlert';
import { numberInputValue, parseNumberInput } from '../components/numberInput';
import { CHOICE_PAGE_SIZE } from '../components/useServerPagination';
import type { TemplateDefinition, TestSuiteDefinition } from '../api';
import { Typography, CircularProgress, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Alert, Tabs, Tab, Paper, Box } from '@mui/material';

function TabPanel(props: { children?: React.ReactNode; index: number; value: number }) {
  const { children, value, index } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`materials-tabpanel-${String(index)}`}
      aria-labelledby={`materials-tab-${String(index)}`}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

/** Associates each tab with the panel it controls, which MUI does not do on its own. */
function tabProps(index: number) {
  return {
    id: `materials-tab-${String(index)}`,
    'aria-controls': `materials-tabpanel-${String(index)}`
  };
}

function TemplateUploadDialog({ open, onClose, templateId }: { open: boolean, onClose: () => void, templateId: string }) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [versionLabel, setVersionLabel] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      const fd = new FormData();
      if (file) fd.append('file', file);
      fd.append('versionLabel', versionLabel);
      return api.createTemplateVersion(templateId, fd);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.templates.versions(templateId) });
      close();
    }
  });

  function close() {
    setFile(null);
    setVersionLabel('');
    mutation.reset();
    onClose();
  }

  return (
    <Dialog open={open} onClose={() => !mutation.isPending && close()} fullWidth maxWidth="sm">
      <DialogTitle>Upload Template Version</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
        <MutationErrorAlert error={mutation.error} />
        <TextField label="Version Label" required fullWidth value={versionLabel} onChange={e => setVersionLabel(e.target.value)} disabled={mutation.isPending} />
        <Button variant="outlined" component="label">
          Select ZIP File
          <input type="file" hidden accept=".zip" onChange={e => setFile(e.target.files?.[0] ?? null)} />
        </Button>
        {file && <Typography variant="body2" role="status">{file.name}</Typography>}
      </DialogContent>
      <DialogActions>
        <Button onClick={close} disabled={mutation.isPending}>Cancel</Button>
        <Button onClick={() => mutation.mutate()} variant="contained" disabled={mutation.isPending || !file || !versionLabel}>
          Upload
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function TestSuiteUploadDialog({ open, onClose, suiteId }: { open: boolean, onClose: () => void, suiteId: string }) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [versionLabel, setVersionLabel] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      const fd = new FormData();
      if (file) fd.append('file', file);
      fd.append('versionLabel', versionLabel);
      return api.createTestSuiteVersion(suiteId, fd);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.testSuites.versions(suiteId) });
      close();
    }
  });

  function close() {
    setFile(null);
    setVersionLabel('');
    mutation.reset();
    onClose();
  }

  return (
    <Dialog open={open} onClose={() => !mutation.isPending && close()} fullWidth maxWidth="sm">
      <DialogTitle>Upload Test Suite Version</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
        <MutationErrorAlert error={mutation.error} />
        <TextField label="Version Label" required fullWidth value={versionLabel} onChange={e => setVersionLabel(e.target.value)} disabled={mutation.isPending} />
        <Button variant="outlined" component="label">
          Select ZIP File
          <input type="file" hidden accept=".zip" onChange={e => setFile(e.target.files?.[0] ?? null)} />
        </Button>
        {file && <Typography variant="body2" role="status">{file.name}</Typography>}
      </DialogContent>
      <DialogActions>
        <Button onClick={close} disabled={mutation.isPending}>Cancel</Button>
        <Button onClick={() => mutation.mutate()} variant="contained" disabled={mutation.isPending || !file || !versionLabel}>
          Upload
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function TemplateVersionList({ templateId }: { templateId: string }) {
  const queryClient = useQueryClient();
  const [uploadOpen, setUploadOpen] = useState(false);
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.templates.versions(templateId),
    queryFn: () => api.getTemplateVersions(templateId)
  });

  const publishMutation = useMutation({
    mutationFn: (versionId: string) => api.publishTemplateVersion(versionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.templates.versions(templateId) });
      // Publishing is what puts a version in front of an assignment, and the picker
      // reads the published set rather than this list. Without this the instructor
      // publishes a version and cannot select it until the page is reloaded.
      void queryClient.invalidateQueries({ queryKey: queryKeys.publishedMaterials });
    }
  });

  if (isLoading) return <CircularProgress size={24} aria-label="Loading template versions" />;

  // A failed request left `data` undefined and the list rendered nothing, so a template
  // that has versions looked exactly like one that has none - which invites uploading a
  // duplicate of a version that is already there.
  if (isError) {
    return (
      <QueryErrorNotice
        message="The versions of this template could not be loaded."
        onRetry={() => void refetch()}
      />
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="subtitle2">Versions</Typography>
        <Button size="small" variant="outlined" onClick={() => setUploadOpen(true)}>Upload Version</Button>
      </Box>
      <MutationErrorAlert error={publishMutation.error} sx={{ mb: 2 }} />
      {data && data.length === 0 && (
        <Typography variant="body2" color="text.secondary">No versions uploaded yet.</Typography>
      )}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {data?.map(v => (
          <Paper key={v.id} variant="outlined" sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Box>
              <Typography variant="body2" sx={{ fontWeight: 'bold' }}>{v.versionLabel}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                {v.contentHash.substring(0, 8)} • {v.fileCount} files • {v.totalBytes} bytes
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                {v.publishedAt ? `Published ${new Date(v.publishedAt).toLocaleString()}` : 'Draft'}
              </Typography>
            </Box>
            {!v.publishedAt && (
              <Button size="small" onClick={() => publishMutation.mutate(v.id)} disabled={publishMutation.isPending}>Publish</Button>
            )}
          </Paper>
        ))}
      </Box>
      <TemplateUploadDialog open={uploadOpen} onClose={() => setUploadOpen(false)} templateId={templateId} />
    </Box>
  );
}

function TestSuiteVersionList({ suiteId }: { suiteId: string }) {
  const queryClient = useQueryClient();
  const [uploadOpen, setUploadOpen] = useState(false);

  const [publishOpen, setPublishOpen] = useState(false);
  const [publishVersion, setPublishVersion] = useState('');
  const [hiddenTests, setHiddenTests] = useState<number | undefined>(0);
  const [publicTests, setPublicTests] = useState<number | undefined>(0);

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.testSuites.versions(suiteId),
    queryFn: () => api.getTestSuiteVersions(suiteId)
  });

  const publishMutation = useMutation({
    mutationFn: () => api.publishTestSuiteVersion(publishVersion, {
      hiddenTestCount: hiddenTests ?? 0,
      publicTestCount: publicTests ?? 0
    }),
    onSuccess: () => {
      // The assignment forms read these versions under the same key, which is the whole
      // point of naming it in one place: while the two spellings disagreed, publishing a
      // version invalidated a cache nobody read and the new version never appeared in the
      // assignment dropdown until the page was reloaded.
      void queryClient.invalidateQueries({ queryKey: queryKeys.testSuites.all });
      void queryClient.invalidateQueries({ queryKey: queryKeys.publishedMaterials });
      closePublish();
    }
  });

  function closePublish() {
    setPublishOpen(false);
    publishMutation.reset();
  }

  if (isLoading) return <CircularProgress size={24} aria-label="Loading test suite versions" />;

  if (isError) {
    return (
      <QueryErrorNotice
        message="The versions of this test suite could not be loaded."
        onRetry={() => void refetch()}
      />
    );
  }

  const countsMissing = hiddenTests === undefined || publicTests === undefined;

  return (
    <Box sx={{ mt: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="subtitle2">Versions</Typography>
        <Button size="small" variant="outlined" onClick={() => setUploadOpen(true)}>Upload Version</Button>
      </Box>
      {data && data.length === 0 && (
        <Typography variant="body2" color="text.secondary">No versions uploaded yet.</Typography>
      )}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {data?.map(v => (
          <Paper key={v.id} variant="outlined" sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Box>
              <Typography variant="body2" sx={{ fontWeight: 'bold' }}>{v.versionLabel}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                {v.contentHash.substring(0, 8)} • Hidden: {v.hiddenTestCount} • Public: {v.publicTestCount}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                {v.publishedAt ? `Published ${new Date(v.publishedAt).toLocaleString()}` : 'Draft'}
              </Typography>
            </Box>
            {!v.publishedAt && (
              <Button size="small" onClick={() => {
                setPublishVersion(v.id);
                setHiddenTests(v.hiddenTestCount);
                setPublicTests(v.publicTestCount);
                setPublishOpen(true);
              }}>Publish</Button>
            )}
          </Paper>
        ))}
      </Box>
      <TestSuiteUploadDialog open={uploadOpen} onClose={() => setUploadOpen(false)} suiteId={suiteId} />

      <Dialog open={publishOpen} onClose={() => !publishMutation.isPending && closePublish()} fullWidth maxWidth="xs">
        <DialogTitle>Publish Test Suite</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <MutationErrorAlert error={publishMutation.error} />
          {/* These counts are what students are told about their run, so an emptied field
              silently becoming zero misreports the suite rather than refusing to submit. */}
          <TextField label="Hidden Test Count" type="number" required value={numberInputValue(hiddenTests)} onChange={e => setHiddenTests(parseNumberInput(e.target.value))} error={hiddenTests === undefined} helperText={hiddenTests === undefined ? 'Required' : undefined} fullWidth disabled={publishMutation.isPending} />
          <TextField label="Public Test Count" type="number" required value={numberInputValue(publicTests)} onChange={e => setPublicTests(parseNumberInput(e.target.value))} error={publicTests === undefined} helperText={publicTests === undefined ? 'Required' : undefined} fullWidth disabled={publishMutation.isPending} />
        </DialogContent>
        <DialogActions>
          <Button onClick={closePublish} disabled={publishMutation.isPending}>Cancel</Button>
          <Button onClick={() => publishMutation.mutate()} variant="contained" disabled={publishMutation.isPending || countsMissing}>Confirm Publish</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

export function MaterialsPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');

  const [tOpen, setTOpen] = useState(false);
  const [tForm, setTForm] = useState<TemplateDefinition>({ templateKey: '', name: '', description: '' });

  const [tsOpen, setTsOpen] = useState(false);
  const [tsForm, setTsForm] = useState<TestSuiteDefinition>({ suiteKey: '', name: '', description: '' });

  const { data: templates, isLoading: tLoading, isError: tFailed, refetch: refetchTemplates } = useQuery({
    queryKey: queryKeys.templates.list,
    queryFn: () => api.getTemplates({ size: CHOICE_PAGE_SIZE })
  });
  const { data: testSuites, isLoading: tsLoading, isError: tsFailed, refetch: refetchTestSuites } = useQuery({
    queryKey: queryKeys.testSuites.list,
    queryFn: () => api.getTestSuites({ size: CHOICE_PAGE_SIZE })
  });

  const createTemplateMutation = useMutation({
    mutationFn: (req: TemplateDefinition) => api.createTemplate(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.templates.all });
      closeTemplateDialog();
    }
  });

  const createTestSuiteMutation = useMutation({
    mutationFn: (req: TestSuiteDefinition) => api.createTestSuite(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.testSuites.all });
      closeTestSuiteDialog();
    }
  });

  function closeTemplateDialog() {
    setTOpen(false);
    setTForm({ templateKey: '', name: '', description: '' });
    createTemplateMutation.reset();
  }

  function closeTestSuiteDialog() {
    setTsOpen(false);
    setTsForm({ suiteKey: '', name: '', description: '' });
    createTestSuiteMutation.reset();
  }

  const filteredTemplates = templates?.content.filter(t =>
    t.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    t.templateKey.toLowerCase().includes(searchQuery.toLowerCase())
  ) ?? [];

  const filteredTestSuites = testSuites?.content.filter(ts =>
    ts.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    ts.suiteKey.toLowerCase().includes(searchQuery.toLowerCase())
  ) ?? [];

  const emptyStateMessage = searchQuery ? `No matches found for "${searchQuery}".` : 'No items found.';

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 2 }}>
        <Typography variant="h4" component="h1">Materials</Typography>
        <TextField
          size="small"
          label="Search"
          placeholder="Search by name or key..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          sx={{ width: 300 }}
        />
      </Box>

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={tab} onChange={(_e, v: number) => setTab(v)} aria-label="Material kind">
          <Tab label="Templates" {...tabProps(0)} />
          <Tab label="Test Suites" {...tabProps(1)} />
        </Tabs>
      </Box>

      <TabPanel value={tab} index={0}>
        <Alert severity="info" sx={{ mb: 3 }}>
          <strong>PUBLIC:</strong> Template content is what students receive. It is publicly visible when published.
        </Alert>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Showing {filteredTemplates.length} {filteredTemplates.length === 1 ? 'template' : 'templates'}
          </Typography>
          <Button variant="contained" onClick={() => setTOpen(true)}>New Template</Button>
        </Box>
        {tLoading ? <CircularProgress aria-label="Loading templates" /> : tFailed ? (
          <QueryErrorNotice message="The templates could not be loaded." onRetry={() => void refetchTemplates()} />
        ) : filteredTemplates.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <Typography color="text.secondary">{emptyStateMessage}</Typography>
          </Paper>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {filteredTemplates.map(t => (
              <Paper key={t.id} sx={{ p: 2 }}>
                <Typography variant="h6">{t.name}</Typography>
                <Typography color="text.secondary" gutterBottom>Key: {t.templateKey}</Typography>
                <Typography variant="body2" gutterBottom>{t.description}</Typography>
                <TemplateVersionList templateId={t.id} />
              </Paper>
            ))}
          </Box>
        )}
      </TabPanel>

      <TabPanel value={tab} index={1}>
        {/* The background was a fixed light-orange hex, which stayed light behind the
            light text of the dark theme and left this warning close to unreadable. */}
        <Alert severity="warning" sx={{ mb: 3 }}>
          <strong>CONFIDENTIAL:</strong> Test suite content is hidden and NEVER shown to students. Keep answers secure.
        </Alert>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Showing {filteredTestSuites.length} {filteredTestSuites.length === 1 ? 'test suite' : 'test suites'}
          </Typography>
          <Button variant="contained" onClick={() => setTsOpen(true)}>New Test Suite</Button>
        </Box>
        {tsLoading ? <CircularProgress aria-label="Loading test suites" /> : tsFailed ? (
          <QueryErrorNotice message="The test suites could not be loaded." onRetry={() => void refetchTestSuites()} />
        ) : filteredTestSuites.length === 0 ? (
          <Paper sx={{ p: 4, textAlign: 'center' }}>
            <Typography color="text.secondary">{emptyStateMessage}</Typography>
          </Paper>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {filteredTestSuites.map(ts => (
              <Paper key={ts.id} sx={{ p: 2 }}>
                <Typography variant="h6">{ts.name}</Typography>
                <Typography color="text.secondary" gutterBottom>Key: {ts.suiteKey}</Typography>
                <Typography variant="body2" gutterBottom>{ts.description}</Typography>
                <TestSuiteVersionList suiteId={ts.id} />
              </Paper>
            ))}
          </Box>
        )}
      </TabPanel>

      <Dialog open={tOpen} onClose={() => !createTemplateMutation.isPending && closeTemplateDialog()} fullWidth maxWidth="sm">
        <form onSubmit={e => { e.preventDefault(); createTemplateMutation.mutate(tForm); }}>
          <DialogTitle>New Template</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <MutationErrorAlert error={createTemplateMutation.error} />
            <TextField label="Template Key" required fullWidth value={tForm.templateKey} onChange={e => setTForm({ ...tForm, templateKey: e.target.value })} disabled={createTemplateMutation.isPending} />
            <TextField label="Name" required fullWidth value={tForm.name} onChange={e => setTForm({ ...tForm, name: e.target.value })} disabled={createTemplateMutation.isPending} />
            <TextField label="Description" fullWidth multiline rows={3} value={tForm.description ?? ''} onChange={e => setTForm({ ...tForm, description: e.target.value })} disabled={createTemplateMutation.isPending} />
          </DialogContent>
          <DialogActions>
            <Button onClick={closeTemplateDialog} disabled={createTemplateMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createTemplateMutation.isPending}>Create</Button>
          </DialogActions>
        </form>
      </Dialog>

      <Dialog open={tsOpen} onClose={() => !createTestSuiteMutation.isPending && closeTestSuiteDialog()} fullWidth maxWidth="sm">
        <form onSubmit={e => { e.preventDefault(); createTestSuiteMutation.mutate(tsForm); }}>
          <DialogTitle>New Test Suite</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <MutationErrorAlert error={createTestSuiteMutation.error} />
            <TextField label="Suite Key" required fullWidth value={tsForm.suiteKey} onChange={e => setTsForm({ ...tsForm, suiteKey: e.target.value })} disabled={createTestSuiteMutation.isPending} />
            <TextField label="Name" required fullWidth value={tsForm.name} onChange={e => setTsForm({ ...tsForm, name: e.target.value })} disabled={createTestSuiteMutation.isPending} />
            <TextField label="Description" fullWidth multiline rows={3} value={tsForm.description ?? ''} onChange={e => setTsForm({ ...tsForm, description: e.target.value })} disabled={createTestSuiteMutation.isPending} />
          </DialogContent>
          <DialogActions>
            <Button onClick={closeTestSuiteDialog} disabled={createTestSuiteMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createTestSuiteMutation.isPending}>Create</Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
}
