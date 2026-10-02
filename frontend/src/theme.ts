// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { createTheme } from "@mui/material/styles";

const inkRamp = {
  50: "#E9EAEC",
  100: "#DDDEE1",
  200: "#C4C7CC",
  300: "#ACAFB7",
  400: "#9497A1",
  500: "#7B808C",
  600: "#636877",
  700: "#4A5161",
  800: "#32394C",
  900: "#192237",
  950: "#0D162C",
};

const mintRamp = {
  50: "#DDFCF2",
  100: "#C5FAE8",
  200: "#94F6D6",
  300: "#64F2C3",
  400: "#33EEB1",
  500: "#03EA9E",
  600: "#03C385",
  700: "#049D6D",
  800: "#047654",
  900: "#04503B",
  950: "#053C2F",
};

export const createAppTheme = (mode: "light" | "dark") => {
  return createTheme({
    palette: {
      mode,
      primary: {
        main: mode === "light" ? "#2563EB" : "#60A5FA",
      },
      secondary: {
        main: mode === "light" ? "#475569" : "#94A3B8",
        contrastText: inkRamp[950],
      },
      error: {
        main: mode === "light" ? "#DC2626" : "#F87171",
      },
      warning: {
        main: mode === "light" ? "#B45309" : "#F59E0B",
      },
      success: {
        main: mode === "light" ? mintRamp[800] : mintRamp[500],
        contrastText: mode === "light" ? "#FFFFFF" : inkRamp[950],
      },
      background: {
        default: mode === "light" ? "#F8FAFC" : inkRamp[950],
        paper: mode === "light" ? "#FFFFFF" : inkRamp[900],
      },
      text: {
        primary: mode === "light" ? inkRamp[950] : "#F5F7FA",
        secondary: mode === "light" ? "#475569" : "#94A3B8",
      },
      divider: mode === "light" ? "#DCE3EA" : inkRamp[800],
    },
    typography: {
      fontFamily: '"Inter", "Helvetica", "Arial", sans-serif',
      h1: { fontWeight: 600 },
      h2: { fontWeight: 600 },
      h3: { fontWeight: 600 },
      h4: { fontWeight: 600 },
      h5: { fontWeight: 600 },
      h6: { fontWeight: 600 },
      button: { textTransform: "none", fontWeight: 500 },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: `
          code, pre {
            font-family: "JetBrains Mono", monospace;
          }
          @media (prefers-reduced-motion: reduce) {
            *, *::before, *::after {
              animation-duration: 0.01ms !important;
              animation-iteration-count: 1 !important;
              scroll-behavior: auto !important;
              transition-duration: 0.01ms !important;
            }
          }
          .MuiDataGrid-root {
            border: 1px solid ${mode === "light" ? "#DCE3EA" : inkRamp[800]};
            border-radius: 6px;
            background-color: ${mode === "light" ? "#FFFFFF" : inkRamp[900]};
          }
          .MuiDataGrid-columnHeaders {
            background-color: ${mode === "light" ? "#F5F7FA" : inkRamp[950]};
            border-bottom: 1px solid ${mode === "light" ? "#DCE3EA" : inkRamp[800]};
          }
          .MuiDataGrid-columnHeaderTitle {
            font-weight: 600;
          }
          .MuiDataGrid-cell {
            border-bottom-color: ${mode === "light" ? "#E9EAEC" : inkRamp[800]};
          }
          .MuiDataGrid-row:hover {
            background-color: ${mode === "light" ? "#F5F7FA" : "#222C40"};
          }
          .MuiDataGrid-footerContainer {
            border-top-color: ${mode === "light" ? "#DCE3EA" : inkRamp[800]};
          }
        `,
      },
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: 6,
            minHeight: 40,
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 6,
            backgroundColor: mode === "light" ? "#FFFFFF" : inkRamp[950],
            "&.Mui-disabled": {
              backgroundColor: mode === "light" ? "#F5F7FA" : inkRamp[900],
            },
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: "none",
          },
        },
      },
      MuiDialogContent: {
        styleOverrides: {
          root: {
            // MUI removes top padding after a title; outlined labels then extend
            // above the scrolling content and are clipped when a field is filled.
            "&.MuiDialogContent-root": { paddingTop: 16 },
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: 6,
          },
        },
      },
      MuiInputLabel: {
        styleOverrides: {
          root: {
            "&.MuiInputLabel-shrink": {
              backgroundColor: mode === "light" ? "#FFFFFF" : inkRamp[950],
              paddingLeft: 4,
              paddingRight: 4,
              marginLeft: -4,
            },
          },
        },
      },
    },
  });
};
