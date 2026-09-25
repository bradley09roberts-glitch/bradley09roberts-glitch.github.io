import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'
import globals from 'globals'

export default tseslint.config(
  { ignores: ['out/**', 'dist/**', 'node_modules/**', 'test-results/**', 'playwright-report/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      'no-console': 'error',
    },
  },
  {
    files: ['src/main/**', 'src/preload/**', 'test/**', '*.config.ts', '*.config.mjs'],
    languageOptions: { globals: { ...globals.node } },
  },
  {
    files: ['src/renderer/**'],
    languageOptions: { globals: { ...globals.browser } },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
    },
  },
  {
    // src/shared is pure TypeScript: no Electron, Node, DOM or React (CLAUDE.md "Purity").
    files: ['src/shared/**'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { group: ['electron', 'electron/*'], message: 'src/shared must not import Electron.' },
            {
              group: ['node:*', 'fs', 'path', 'os', 'child_process'],
              message: 'src/shared must not import Node APIs.',
            },
            {
              group: ['react', 'react-dom', 'react/*'],
              message: 'src/shared must not import React.',
            },
          ],
        },
      ],
      'no-restricted-globals': ['error', 'window', 'document', 'process'],
    },
  },
  {
    // Console output is allowed in tests and in the logger's own sink.
    files: ['**/*.test.ts', 'test/**', 'src/main/logger.ts'],
    rules: { 'no-console': 'off' },
  },
)
