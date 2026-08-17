// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery, useQueries } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { CHOICE_PAGE_SIZE } from '../components/useServerPagination';

/**
 * Loads the published templates, test suites and runtimes an assignment can be pointed at.
 *
 * Readiness is taken from the queries' own state rather than from whether data arrived.
 * Inferring it from absence conflated "still loading" with "failed": once a request had
 * exhausted its retries its data stayed undefined, so the caller was told to keep waiting
 * forever and the assignment page held a spinner that could never resolve.
 */
export function useAssignmentMaterials() {
  const templates = useQuery({
    queryKey: queryKeys.templates.list,
    queryFn: () => api.getTemplates({ size: CHOICE_PAGE_SIZE })
  });

  const suites = useQuery({
    queryKey: queryKeys.testSuites.list,
    queryFn: () => api.getTestSuites({ size: CHOICE_PAGE_SIZE })
  });

  const runtimes = useQuery({
    queryKey: queryKeys.runtimes,
    queryFn: () => api.getRuntimes()
  });

  const templatesData = templates.data;
  const suitesData = suites.data;

  const templateVersionsQueries = useQueries({
    queries: (templatesData?.content ?? []).map(t => ({
      queryKey: queryKeys.templates.versions(t.id),
      queryFn: () => api.getTemplateVersions(t.id)
    }))
  });

  const suiteVersionsQueries = useQueries({
    queries: (suitesData?.content ?? []).map(s => ({
      queryKey: queryKeys.testSuites.versions(s.id),
      queryFn: () => api.getTestSuiteVersions(s.id)
    }))
  });

  const publishedTemplateVersions = (templatesData?.content ?? []).flatMap((t, i) => {
    const versions = templateVersionsQueries[i]?.data ?? [];
    return versions.filter(v => v.publishedAt).map(v => ({
      id: v.id,
      label: `${t.name} — ${v.versionLabel}`
    }));
  });

  const publishedSuiteVersions = (suitesData?.content ?? []).flatMap((s, i) => {
    const versions = suiteVersionsQueries[i]?.data ?? [];
    return versions.filter(v => v.publishedAt).map(v => ({
      id: v.id,
      label: `${s.name} — ${v.versionLabel} (${v.hiddenTestCount} hidden / ${v.publicTestCount} public)`
    }));
  });

  return {
    publishedTemplateVersions,
    publishedSuiteVersions,
    runtimes: runtimes.data ?? [],
    isLoading:
      templates.isPending ||
      suites.isPending ||
      runtimes.isPending ||
      templateVersionsQueries.some(q => q.isPending) ||
      suiteVersionsQueries.some(q => q.isPending),
    isError:
      templates.isError ||
      suites.isError ||
      runtimes.isError ||
      templateVersionsQueries.some(q => q.isError) ||
      suiteVersionsQueries.some(q => q.isError)
  };
}
