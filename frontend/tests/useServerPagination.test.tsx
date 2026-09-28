// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useServerPagination } from '../src/components/useServerPagination';

describe('useServerPagination', () => {
  it('serializes the selected sort and returns to the first page', () => {
    const { result } = renderHook(() => useServerPagination());

    act(() => result.current.setPaginationModel({ page: 2, pageSize: 20 }));
    act(() => result.current.setSortModel([{ field: 'name', sort: 'asc' }]));

    expect(result.current.params).toEqual({ page: '0', size: '20', sort: 'name,asc' });
    expect(result.current.sortModel).toEqual([{ field: 'name', sort: 'asc' }]);
  });
});