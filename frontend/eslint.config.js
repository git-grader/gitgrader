import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';
import jsxA11y from 'eslint-plugin-jsx-a11y';

export default tseslint.config(
  { ignores: ['dist', 'coverage'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.strictTypeChecked, jsxA11y.flatConfigs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        project: ['./tsconfig.json'],
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/restrict-template-expressions': 'off',
      '@typescript-eslint/no-confusing-void-expression': 'off'
    },
  },
  // The Playwright smoke scripts are plain Node ES modules. They were outside every
  // `files` block, so ESLint still parsed them and reported success while applying zero
  // rules: a vacuous pass that made `npm run lint` green over 900-odd lines of
  // unreviewed code. They get the recommended JavaScript rules and both global sets,
  // because each file legitimately spans two contexts: the script body runs in Node,
  // while every `page.evaluate`, `addInitScript` and `waitForFunction` callback is
  // serialised and executed inside the browser, where `document`, `location` and
  // `getComputedStyle` are defined and flagging them would be a false positive.
  // They stay out of the TypeScript project because they are not part of the
  // application and `tsc` has no `allowJs` to check them with.
  {
    extends: [js.configs.recommended],
    files: ['**/*.mjs'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.node, ...globals.browser },
    },
  }
);
