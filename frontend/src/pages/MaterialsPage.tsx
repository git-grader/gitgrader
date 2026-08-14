// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { CHOICE_PAGE_SIZE } from '../components/useServerPagination';
import type { TemplateDefinition, TestSuiteDefinition } from '../api';
import { ApiProblem } from '../api/client';
import { Typography, CircularProgress, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Alert, Tabs, Tab, Paper, Box } from '@mui/material';

function TabPanel(props: { children?: React.ReactNode; index: number; value: number }) {
  const { children, value, index, ...other } = props;
  return (
    <div role="tabpanel" hidden={value !== index} id={`tabpanel-${index}`} {...other}>
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
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
      void queryClient.invalidateQueries({ queryKey: ['templates', templateId, 'versions'] });
      onClose();
    }
  });

  const err = mutation.error as ApiProblem | null;

  return (
    <Dialog open={open} onClose={() => !mutation.isPending && onClose()} fullWidth maxWidth="sm">
      <DialogTitle>Upload Template Version</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
        {err && <Alert severity="error">{err.detail || err.title}</Alert>}
        <TextField label="Version Label" required fullWidth value={versionLabel} onChange={e => setVersionLabel(e.target.value)} disabled={mutation.isPending} />
        <Button variant="outlined" component="label">
          Select ZIP File
          <input type="file" hidden accept=".zip" onChange={e => setFile(e.target.files?.[0] || null)} />
        </Button>
        {file && <Typography variant="body2">{file.name}</Typography>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={mutation.isPending}>Cancel</Button>
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
      void queryClient.invalidateQueries({ queryKey: ['testSuites', suiteId, 'versions'] });
      onClose();
    }
  });

  const err = mutation.error as ApiProblem | null;

  return (
    <Dialog open={open} onClose={() => !mutation.isPending && onClose()} fullWidth maxWidth="sm">
      <DialogTitle>Upload Test Suite Version</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
        {err && <Alert severity="error">{err.detail || err.title}</Alert>}
        <TextField label="Version Label" required fullWidth value={versionLabel} onChange={e => setVersionLabel(e.target.value)} disabled={mutation.isPending} />
        <Button variant="outlined" component="label">
          Select ZIP File
          <input type="file" hidden accept=".zip" onChange={e => setFile(e.target.files?.[0] || null)} />
        </Button>
        {file && <Typography variant="body2">{file.name}</Typography>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={mutation.isPending}>Cancel</Button>
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
  const { data, isLoading } = useQuery({
    queryKey: ['templates', templateId, 'versions'],
    queryFn: () => api.getTemplateVersions(templateId)
  });

  const publishMutation = useMutation({
    mutationFn: (versionId: string) => api.publishTemplateVersion(versionId),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['templates', templateId, 'versions'] }); }
  });

  if (isLoading) return <CircularProgress size={24} />;

  const err = publishMutation.error as ApiProblem | null;

  return (
    <Box sx={{ mt: 2 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
        <Typography variant="subtitle2">Versions</Typography>
        <Button size="small" variant="outlined" onClick={() => setUploadOpen(true)}>Upload Version</Button>
      </div>
      {err && <Alert severity="error" sx={{ mb: 2 }}>{err.detail || err.title}</Alert>}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {data?.map(v => (
          <Paper key={v.id} variant="outlined" sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Typography variant="body2" sx={{ fontWeight: 'bold' }}>{v.versionLabel}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                {v.contentHash.substring(0, 8)} • {v.fileCount} files • {v.totalBytes} bytes
              </Typography>
              <Typography variant="caption" color={v.publishedAt ? "success.main" : "text.secondary"} sx={{ display: 'block' }}>
                {v.publishedAt ? `Published ${new Date(v.publishedAt).toLocaleString()}` : 'Draft'}
              </Typography>
            </div>
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
  
  // Publish form state
  const [publishOpen, setPublishOpen] = useState(false);
  const [publishVersion, setPublishVersion] = useState('');
  const [hiddenTests, setHiddenTests] = useState(0);
  const [publicTests, setPublicTests] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['testSuites', suiteId, 'versions'],
    queryFn: () => api.getTestSuiteVersions(suiteId)
  });

  const publishMutation = useMutation({
    mutationFn: () => api.publishTestSuiteVersion(publishVersion, { hiddenTestCount: hiddenTests, publicTestCount: publicTests }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['testSuites', suiteId, 'versions'] });
      setPublishOpen(false);
    }
  });

  if (isLoading) return <CircularProgress size={24} />;

  const err = publishMutation.error as ApiProblem | null;

  return (
    <Box sx={{ mt: 2 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
        <Typography variant="subtitle2">Versions</Typography>
        <Button size="small" variant="outlined" onClick={() => setUploadOpen(true)}>Upload Version</Button>
      </div>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {data?.map(v => (
          <Paper key={v.id} variant="outlined" sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Typography variant="body2" sx={{ fontWeight: 'bold' }}>{v.versionLabel}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                {v.contentHash.substring(0, 8)} • Hidden: {v.hiddenTestCount} • Public: {v.publicTestCount}
              </Typography>
              <Typography variant="caption" color={v.publishedAt ? "success.main" : "text.secondary"} sx={{ display: 'block' }}>
                {v.publishedAt ? `Published ${new Date(v.publishedAt).toLocaleString()}` : 'Draft'}
              </Typography>
            </div>
            {!v.publishedAt && (
              <Button size="small" onClick={() => {
                setPublishVersion(v.id);
                setHiddenTests(v.hiddenTestCount || 0);
                setPublicTests(v.publicTestCount || 0);
                setPublishOpen(true);
              }}>Publish</Button>
            )}
          </Paper>
        ))}
      </Box>
      <TestSuiteUploadDialog open={uploadOpen} onClose={() => setUploadOpen(false)} suiteId={suiteId} />
      
      <Dialog open={publishOpen} onClose={() => !publishMutation.isPending && setPublishOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Publish Test Suite</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {err && <Alert severity="error">{err.detail || err.title}</Alert>}
          <TextField label="Hidden Test Count" type="number" value={hiddenTests} onChange={e => setHiddenTests(parseInt(e.target.value) || 0)} fullWidth disabled={publishMutation.isPending} />
          <TextField label="Public Test Count" type="number" value={publicTests} onChange={e => setPublicTests(parseInt(e.target.value) || 0)} fullWidth disabled={publishMutation.isPending} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPublishOpen(false)} disabled={publishMutation.isPending}>Cancel</Button>
          <Button onClick={() => publishMutation.mutate()} variant="contained" disabled={publishMutation.isPending}>Confirm Publish</Button>
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

  const { data: templates, isLoading: tLoading, isError: tFailed, refetch: refetchTemplates } = useQuery({ queryKey: ['templates'], queryFn: () => api.getTemplates({ size: CHOICE_PAGE_SIZE }) });
  const { data: testSuites, isLoading: tsLoading, isError: tsFailed, refetch: refetchTestSuites } = useQuery({ queryKey: ['testSuites'], queryFn: () => api.getTestSuites({ size: CHOICE_PAGE_SIZE }) });

  const createTemplateMutation = useMutation({
    mutationFn: (req: TemplateDefinition) => api.createTemplate(req),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['templates'] }); setTOpen(false); }
  });

  const createTestSuiteMutation = useMutation({
    mutationFn: (req: TestSuiteDefinition) => api.createTestSuite(req),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['testSuites'] }); setTsOpen(false); }
  });

  const filteredTemplates = templates?.content.filter(t => 
    t.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
    t.templateKey.toLowerCase().includes(searchQuery.toLowerCase())
  ) || [];

  const filteredTestSuites = testSuites?.content.filter(ts => 
    ts.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
    ts.suiteKey.toLowerCase().includes(searchQuery.toLowerCase())
  ) || [];

  const emptyStateMessage = searchQuery ? `No matches found for "${searchQuery}".` : "No items found.";

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
        <Tabs value={tab} onChange={(_e, v: number) => setTab(v)}>
          <Tab label="Templates" />
          <Tab label="Test Suites" />
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
        {tLoading ? <CircularProgress /> : tFailed ? (
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
        <Alert severity="warning" sx={{ mb: 3, bgcolor: '#fff4e5' }}>
          <strong>CONFIDENTIAL:</strong> Test suite content is hidden and NEVER shown to students. Keep answers secure.
        </Alert>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="body2" color="text.secondary">
            Showing {filteredTestSuites.length} {filteredTestSuites.length === 1 ? 'test suite' : 'test suites'}
          </Typography>
          <Button variant="contained" onClick={() => setTsOpen(true)}>New Test Suite</Button>
        </Box>
        {tsLoading ? <CircularProgress /> : tsFailed ? (
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

      <Dialog open={tOpen} onClose={() => !createTemplateMutation.isPending && setTOpen(false)} fullWidth maxWidth="sm">
        <form onSubmit={e => { e.preventDefault(); createTemplateMutation.mutate(tForm); }}>
          <DialogTitle>New Template</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {(createTemplateMutation.error as ApiProblem | null) && <Alert severity="error">{(createTemplateMutation.error as ApiProblem).detail || (createTemplateMutation.error as ApiProblem).title}</Alert>}
            <TextField label="Template Key" required fullWidth value={tForm.templateKey} onChange={e => setTForm({...tForm, templateKey: e.target.value})} disabled={createTemplateMutation.isPending} />
            <TextField label="Name" required fullWidth value={tForm.name} onChange={e => setTForm({...tForm, name: e.target.value})} disabled={createTemplateMutation.isPending} />
            <TextField label="Description" fullWidth multiline rows={3} value={tForm.description} onChange={e => setTForm({...tForm, description: e.target.value})} disabled={createTemplateMutation.isPending} />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setTOpen(false)} disabled={createTemplateMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createTemplateMutation.isPending}>Create</Button>
          </DialogActions>
        </form>
      </Dialog>

      <Dialog open={tsOpen} onClose={() => !createTestSuiteMutation.isPending && setTsOpen(false)} fullWidth maxWidth="sm">
        <form onSubmit={e => { e.preventDefault(); createTestSuiteMutation.mutate(tsForm); }}>
          <DialogTitle>New Test Suite</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {(createTestSuiteMutation.error as ApiProblem | null) && <Alert severity="error">{(createTestSuiteMutation.error as ApiProblem).detail || (createTestSuiteMutation.error as ApiProblem).title}</Alert>}
            <TextField label="Suite Key" required fullWidth value={tsForm.suiteKey} onChange={e => setTsForm({...tsForm, suiteKey: e.target.value})} disabled={createTestSuiteMutation.isPending} />
            <TextField label="Name" required fullWidth value={tsForm.name} onChange={e => setTsForm({...tsForm, name: e.target.value})} disabled={createTestSuiteMutation.isPending} />
            <TextField label="Description" fullWidth multiline rows={3} value={tsForm.description} onChange={e => setTsForm({...tsForm, description: e.target.value})} disabled={createTestSuiteMutation.isPending} />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setTsOpen(false)} disabled={createTestSuiteMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createTestSuiteMutation.isPending}>Create</Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
}
