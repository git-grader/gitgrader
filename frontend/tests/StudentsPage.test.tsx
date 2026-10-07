// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StudentsPage } from "../src/pages/StudentsPage";
import { renderWithProviders, server } from "./harness";

const ada = {
  id: "student-1",
  studentUsername: "ada",
  firstName: "Ada",
  lastName: "Lovelace",
  email: "ada@example.edu",
  status: "VERIFIED_BY_INSTRUCTOR",
};
const grace = {
  id: "student-2",
  studentUsername: "grace",
  firstName: "Grace",
  lastName: "Hopper",
  email: "grace@example.edu",
  status: "SELF_REGISTERED",
};

describe("StudentsPage", () => {

  it("loads every bounded page and filters the complete student list locally", async () => {
    const requestedPages: string[] = [];
    server.use(
      http.get("/api/v1/students", ({ request }) => {
        const page = new URL(request.url).searchParams.get("page") ?? "0";
        requestedPages.push(page);
        const content = page === "0" ? [ada] : [grace];
        return HttpResponse.json({
          content,
          totalElements: 2,
          totalPages: 2,
          size: 200,
          number: Number(page),
        });
      }),
    );
    const user = userEvent.setup();

    renderWithProviders(<StudentsPage />);

    expect(await screen.findByText("Ada")).toBeInTheDocument();
    expect(await screen.findByText("Grace")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Student results" })).toBeInTheDocument();
    expect(requestedPages).toEqual(["0", "1"]);

    await user.type(screen.getByRole("textbox", { name: "Search" }), "grace");

    expect(screen.queryByText("Ada")).not.toBeInTheDocument();
    expect(screen.getByText("Grace")).toBeInTheDocument();
  });
});
