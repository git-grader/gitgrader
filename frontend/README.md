# GitGrader Frontend

Open-source self-hostable platform for grading programming assignments submitted over Git.

## Stack
- Vite 8.x + React 19.x + TypeScript
- MUI v9 for UI components
- React Router 8.x
- TanStack Query v5
- Zod

The compiler and the linter are pinned apart on purpose: `npm run typecheck` runs
the TypeScript 7 compiler through the `typescript7` alias, while `typescript` stays
at 6.x because that is what typescript-eslint supports. Raising the alias is safe;
raising `typescript` ahead of typescript-eslint breaks linting.

## Scripts
- `npm run dev`: Start Vite dev server
- `npm run build`: Typecheck and build for production
- `npm run lint`: Run ESLint
- `npm run test:ci`: Run Vitest test suite with coverage
- `npm run preview`: Preview built production build locally

## Browser layout regression checks

Install Chromium once with `npx playwright install chromium`, then run
`npm run test:browser` from this directory. To use an installed Chrome instead,
set `PLAYWRIGHT_CHROMIUM_EXECUTABLE` to its executable path.

The checks start and stop their own local Vite server and use synthetic API
responses; no backend, production session, or credentials are needed. They cover
light and dark layouts at 320, 390, 768, 1024, and 1440 pixels, grid visibility,
full-height panels and viewport resizing, pagination, navigation with shared cached
course choices, prefilled dialog labels,
class attention badges, and public result and registration confirmation layouts.

Instructor feature checks also exercise searchable runtime choices, class
attention filters, exact-submission grading results, and confirmed grading retries.

Class roster checks cover hiding assignment summaries and resetting combined filters.
Set `UI_BROWSER_SCREENSHOT_DIR` to a directory to save review screenshots.

See [Instructor workflows](FEATURES.md) for saved views, publication readiness,
draft duplication, bulk verification, deadlines and dashboard follow-up.
