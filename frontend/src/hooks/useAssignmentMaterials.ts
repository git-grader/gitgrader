import { useQuery, useQueries } from '@tanstack/react-query';
import { api } from '../api';
import { CHOICE_PAGE_SIZE } from '../components/useServerPagination';

export function useAssignmentMaterials() {
  const { data: templatesData } = useQuery({
    queryKey: ['templates'],
    queryFn: () => api.getTemplates({ size: CHOICE_PAGE_SIZE })
  });

  const { data: suitesData } = useQuery({
    queryKey: ['test-suites'],
    queryFn: () => api.getTestSuites({ size: CHOICE_PAGE_SIZE })
  });

  const { data: runtimes } = useQuery({
    queryKey: ['runtimes'],
    queryFn: () => api.getRuntimes()
  });

  const templateVersionsQueries = useQueries({
    queries: (templatesData?.content || []).map(t => ({
      queryKey: ['templates', t.id, 'versions'],
      queryFn: () => api.getTemplateVersions(t.id)
    }))
  });

  const suiteVersionsQueries = useQueries({
    queries: (suitesData?.content || []).map(s => ({
      queryKey: ['test-suites', s.id, 'versions'],
      queryFn: () => api.getTestSuiteVersions(s.id)
    }))
  });

  const publishedTemplateVersions = (templatesData?.content || []).flatMap((t, i) => {
    const versions = templateVersionsQueries[i]?.data || [];
    return versions.filter(v => v.publishedAt).map(v => ({
      id: v.id,
      label: `${t.name} — ${v.versionLabel}`
    }));
  });

  const publishedSuiteVersions = (suitesData?.content || []).flatMap((s, i) => {
    const versions = suiteVersionsQueries[i]?.data || [];
    return versions.filter(v => v.publishedAt).map(v => ({
      id: v.id,
      label: `${s.name} — ${v.versionLabel} (${v.hiddenTestCount} hidden / ${v.publicTestCount} public)`
    }));
  });

  return {
    publishedTemplateVersions,
    publishedSuiteVersions,
    runtimes: runtimes || [],
    isLoading: !templatesData || !suitesData || !runtimes || 
      templateVersionsQueries.some(q => q.isLoading) || 
      suiteVersionsQueries.some(q => q.isLoading)
  };
}
