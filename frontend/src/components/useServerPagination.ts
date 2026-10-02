// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useLocation } from 'react-router';
import { useTableSettings } from './tablePreferences';
import { useState } from "react";
import type { GridSortModel } from "@mui/x-data-grid";

/**
 * The page size used when a list only needs the choices, not a pager.
 *
 * A filter dropdown has nowhere to put pagination, so it asks for a page large enough to
 * hold every realistic option. The server still caps what it returns, which is the point:
 * the request is explicit rather than silently relying on a default.
 */
export const CHOICE_PAGE_SIZE = "200";

interface ServerPagination {
  readonly paginationModel: { page: number; pageSize: number };
  readonly setPaginationModel: (model: {
    page: number;
    pageSize: number;
  }) => void;
  readonly sortModel: GridSortModel;
  readonly setSortModel: (model: GridSortModel) => void;
  /** Query parameters to send with the request, as the API helpers expect them. */
  readonly params: { page: string; size: string; sort?: string };
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
  const { pathname } = useLocation();
  const { settings } = useTableSettings(pathname, pageSize === 50 || pageSize === 100 ? pageSize : 20);
  const [paginationModel, setPaginationModel] = useState<{ page: number; pageSize: number }>({ page: 0, pageSize: settings.model.pageSize });
  const [sortModel, setSortModelState] = useState<GridSortModel>(settings.model.sort);

  function setSortModel(model: GridSortModel) {
    setPaginationModel((current) => ({ ...current, page: 0 }));
    setSortModelState(model);
  }

  const sort = sortModel[0]?.sort
    ? `${sortModel[0].field},${sortModel[0].sort}`
    : undefined;

  return {
    paginationModel,
    setPaginationModel,
    sortModel,
    setSortModel,
    params: {
      page: String(paginationModel.page),
      size: String(paginationModel.pageSize),
      ...(sort ? { sort } : {}),
    },
  };
}
