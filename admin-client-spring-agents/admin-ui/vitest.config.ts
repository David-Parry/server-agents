import { defineConfig } from 'vitest/config';
import { fileURLToPath } from 'node:url';

const coveragePhase = process.env.COVERAGE_PHASE ?? 'baseline';
const phaseThresholds: Record<string, { lines: number; functions: number; branches: number; statements: number }> = {
  baseline: { lines: 35, functions: 15, branches: 70, statements: 35 },
  intermediate: { lines: 60, functions: 45, branches: 70, statements: 60 },
  target: { lines: 85, functions: 85, branches: 75, statements: 85 },
};
const thresholds = phaseThresholds[coveragePhase] ?? phaseThresholds.baseline;

export default defineConfig({
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      include: ['src/lib/api.ts', 'src/lib/auth-context.tsx'],
      exclude: ['**/*.test.ts', '**/*.test.tsx'],
      thresholds,
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
});
