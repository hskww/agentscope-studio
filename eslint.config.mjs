// @ts-check

import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';

// ---------------------------------------------------------------------------
// Browser-bundle safety guards for the client package
//
// packages/shared mixes browser-safe contracts with server-only modules that
// touch fs / child_process / dotenv / readline and that reach into
// packages/server. Importing one of those from the client makes Vite replace
// the Node builtin with a stub, which throws "Module ... has been externalized
// for browser compatibility" the moment the chunk is evaluated (the app used to
// white-screen because a settings page imported config/friday for its types).
//
// `regex` is used instead of `group` on purpose: `group` follows gitignore
// semantics, where matching a directory also matches everything below it, so
// banning `@shared` would wrongly flag `@shared/types` as well.
// ---------------------------------------------------------------------------

/** Server-only modules inside packages/shared (alias and relative forms). */
const BROWSER_UNSAFE_SHARED_IMPORTS = [
    '^(?:@shared|(?:\\.\\./)+(?:packages/)?shared/src)',
    '(?:$',
    '|/(?:index|utils/(?:banner|terminal))(?:\\.\\w+)?$',
    // Everything under config/ except the browser-safe contracts.
    '|/config(?:/(?!client(?:\\.\\w+)?|friday-common(?:\\.\\w+)?)[\\w.-]+|/index)?(?:\\.\\w+)?$',
    ')',
].join('');

/** Deep imports into the Node server package. */
const BROWSER_UNSAFE_SERVER_IMPORTS =
    '^(?:@server|(?:\\.\\./)+(?:packages/)?server/src)/';

/** Node built-ins and server-only runtimes that have no browser twin. */
const BROWSER_UNSAFE_NODE_IMPORTS = [
    '^(?:node:)?',
    '(?:fs|path|os|child_process|readline|net|http|https|crypto|util|process|dotenv|better-sqlite3|socket\\.io)',
    '(?:/.*)?$',
].join('');

export default tseslint.config(
    {
        ignores: [
            'bin/**',
            '**/proto/**',
            'node_modules/**',
            'dist/**',
            'build/**',
            'coverage/**',
            '.next/**',
            '.nuxt/**',
            '.vuepress/dist/**',
            '.vitepress/**',
            'docs/.vitepress/cache/**',
            'docs/.vitepress/dist/**',
            '.serverless/**',
            '.fusebox/**',
            '.dynamodb/**',
            '**/*.min.js',
            '**/*.min.css',
            '**/*.log',
            '**/*.lock',
            'package-lock.json',
            'yarn.lock',
            'pnpm-lock.yaml',
            '**/__pycache__/**',
            '**/*.pyc',
            '.git/**',
            '.vscode/**',
            '.idea/**',
            '**/cache/**',
        ],
    },
    eslint.configs.recommended,
    tseslint.configs.recommended,
    {
        files: ['packages/client/src/**/*.{ts,tsx}'],
        rules: {
            'no-restricted-imports': [
                'error',
                {
                    patterns: [
                        {
                            regex: BROWSER_UNSAFE_SHARED_IMPORTS,
                            // A type-only import is erased by the bundler, so it
                            // can never drag a Node module into the browser.
                            allowTypeImports: true,
                            message:
                                'Browser code must not import Node-only modules: fs/child_process/dotenv get externalized by Vite and throw as soon as the chunk is evaluated. Import the browser-safe contracts from @shared/config/friday-common or @shared/config/client, or move the logic to packages/server and expose it over tRPC/socket.io.',
                        },
                        {
                            regex: BROWSER_UNSAFE_SERVER_IMPORTS,
                            allowTypeImports: true,
                            message:
                                'packages/server is the Node side of the app and cannot be bundled for the browser. Use tRPC or socket.io-client to talk to the server, or move the shared types/constants into packages/shared.',
                        },
                        {
                            regex: BROWSER_UNSAFE_NODE_IMPORTS,
                            allowTypeImports: true,
                            message:
                                'This Node built-in / server-only dependency has no browser implementation, so Vite replaces it with a stub that throws on access. Keep it on the server side (packages/server).',
                        },
                    ],
                },
            ],
        },
    },
);
