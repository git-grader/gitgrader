// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';

/**
 * Loads the published templates, test suites and runtimes an assignment can be pointed at.
 *
 * Two requests, whatever the size of the catalogue. This previously listed the templates
 * and suites and then asked each one for its versions, so a course with a hundred of each
 * opened the assignment page with several hundred requests and held a spinner until the
 * last one landed. The backend now returns the published set in one response.
 *
 * Readiness is taken from the queries' own state rather than from whether data arrived.
 * Inferring it from absence conflated "still loading" with "failed": once a request had
 * exhausted its retries its data stayed undefined, so the caller was told to keep waiting
 * forever and the assignment page held a spinner that could never resolve.
 */
export function useAssignmentMaterials() {
  const materials = useQuery({
    queryKey: queryKeys.publishedMaterials,
    queryFn: () => api.getPublishedMaterials()
  });

  const runtimes = useQuery({
    queryKey: queryKeys.runtimes,
    queryFn: () => api.getRuntimes()
  });

  const publishedTemplateVersions = (materials.data?.templateVersions ?? []).map(v => ({
    id: v.id,
    label: `${v.templateName} — ${v.versionLabel}`
  }));

  const publishedSuiteVersions = (materials.data?.suiteVersions ?? []).map(v => ({
    id: v.id,
    label: `${v.suiteName} — ${v.versionLabel} (${v.hiddenTestCount} hidden / ${v.publicTestCount} public)`
  }));

  return {
    publishedTemplateVersions,
    publishedSuiteVersions,
    runtimes: runtimes.data ?? [],
    isLoading: materials.isPending || runtimes.isPending,
    isError: materials.isError || runtimes.isError
  };
}
