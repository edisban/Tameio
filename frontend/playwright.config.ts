import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests through the real stack: the Spring Boot API, PostgreSQL and S3 storage.
 * Either run everything in containers (docker compose --profile app up -d --build) and set
 * E2E_EXTERNAL=1, or start the infrastructure and the backend yourself and let Playwright start Vite.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  // Every test signs up, registers a business and goes through real OCR. Right after the stack starts
  // (a cold JVM) with a browser per core, the 30 s and 5 s defaults were too tight
  timeout: 60_000,
  expect: { timeout: 10_000 },
  workers: process.env.CI ? 2 : '50%',
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5180',
    locale: 'el-GR',
    timezoneId: 'Europe/Athens',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // E2E_EXTERNAL: the app already runs (docker compose --profile app, as in CI); otherwise start the dev server
  webServer: process.env.E2E_EXTERNAL
    ? undefined
    : { command: 'npm run dev', url: 'http://localhost:5180', reuseExistingServer: true },
})
