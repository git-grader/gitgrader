// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { expect, it } from "vitest";
import { createAppTheme } from "../src/theme";

it("gives enabled fields a clear surface and keeps disabled fields distinct", () => {
  const lightTheme = createAppTheme("light");
  const darkTheme = createAppTheme("dark");

  expect(
    lightTheme.components?.MuiOutlinedInput?.styleOverrides?.root,
  ).toMatchObject({
    backgroundColor: "#FFFFFF",
    "&.Mui-disabled": { backgroundColor: "#F5F7FA" },
  });
  expect(
    darkTheme.components?.MuiOutlinedInput?.styleOverrides?.root,
  ).toMatchObject({
    backgroundColor: "#0D162C",
    "&.Mui-disabled": { backgroundColor: "#192237" },
  });
});

it("keeps buttons easy to identify and tap", () => {
  expect(
    createAppTheme("light").components?.MuiButton?.styleOverrides?.root,
  ).toMatchObject({ minHeight: 40, borderRadius: 6 });
});
