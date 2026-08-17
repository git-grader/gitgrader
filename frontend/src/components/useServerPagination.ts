// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';

/**
 * The page size used when a list only needs the choices, not a pager.
 *
 * A filter dropdown has nowhere to put pagination, so it asks for a page large enough to
 * hold every realistic option. The server still caps what it returns, which is the point:
 * the request is explicit rather than silently relying on a default.
 */
export const CHOICE_PAGE_SIZE = '200';

interface ServerPagination {
  readonly paginationModel: { page: number; pageSize: number };
  readonly setPaginationModel: (model: { page: number; pageSize: number }) => void;
  /** Query parameters to send with the request, as the API helpers expect them. */
  readonly params: { page: string; size: string };
}

/**
 * Holds the page a server-paged list is showing.
 *
 * These endpoints return a `Page`, and a request without `page` and `size` gets the
 * server's default first page. Feeding only that page to a grid made the grid report the
 * partial count as the total - a course with 34 students displayed "1-20 of 20", which
 * does not merely hide records, it states a wrong number.
 *
 * @param pageSize how many rows to request initially
 * @returns the model to give a DataGrid, its setter, and the query parameters to send
 */
export function useServerPagination(pageSize = 20): ServerPagination {
  const [paginationModel, setPaginationModel] = useState({ page: 0, pageSize });
  return {
    paginationModel,
    setPaginationModel,
    params: { page: String(paginationModel.page), size: String(paginationModel.pageSize) }
  };
}
