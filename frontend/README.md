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
