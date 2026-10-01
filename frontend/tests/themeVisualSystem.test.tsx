// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { expect, test } from 'vitest';
import { createAppTheme } from '../src/theme';

test('defines a calm operational canvas in both color modes', () => {
  const light = createAppTheme('light');
  const dark = createAppTheme('dark');

  expect(light.palette.background.default).toBe('#F8FAFC');
  expect(light.palette.background.paper).toBe('#FFFFFF');
  expect(dark.palette.background.default).toBe('#0D162C');
  expect(dark.palette.background.paper).toBe('#192237');
  expect(light.palette.primary.main).toBe('#2563EB');
  expect(light.palette.success.main).toBe('#03EA9E');
});

test('keeps shared operational controls compact and framed', () => {
  const theme = createAppTheme('light');

  expect(theme.components?.MuiButton?.styleOverrides?.root).toMatchObject({
    borderRadius: 6,
    minHeight: 40
  });
  expect(theme.components?.MuiDialog?.styleOverrides?.paper).toMatchObject({
    borderRadius: 6
  });
  expect(theme.components?.MuiOutlinedInput?.styleOverrides?.root).toMatchObject({
    borderRadius: 6
  });
});