// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { expect, test } from 'vitest';
import { getContrastRatio } from '@mui/material/styles';
import { createAppTheme } from '../src/theme';

test('defines a calm operational canvas in both color modes', () => {
  const light = createAppTheme('light');
  const dark = createAppTheme('dark');

  expect(light.palette.background.default).toBe('#F8FAFC');
  expect(light.palette.background.paper).toBe('#FFFFFF');
  expect(dark.palette.background.default).toBe('#0D162C');
  expect(dark.palette.background.paper).toBe('#192237');
  expect(light.palette.primary.main).toBe('#2563EB');
  expect(getContrastRatio(light.palette.success.main, light.palette.background.paper)).toBeGreaterThanOrEqual(4.5);
  expect(getContrastRatio(dark.palette.success.main, dark.palette.background.paper)).toBeGreaterThanOrEqual(4.5);
  for (const theme of [light, dark]) {
    expect(getContrastRatio(theme.palette.success.main, theme.palette.success.contrastText)).toBeGreaterThanOrEqual(4.5);
  }
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