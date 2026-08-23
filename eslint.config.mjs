import frontendConfig from "./frontend/eslint.config.mjs";

export default [
  {
    ignores: [
      "backend/**",
      "frontend/node_modules/**",
      "frontend/.next/**",
      "frontend/.next-stale-*/**",
      "frontend/.next.cache-*/**",
      "frontend/next-env.d.ts",
      "frontend/out/**",
      "frontend/coverage/**",
    ],
  },
  ...frontendConfig,
];
