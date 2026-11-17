import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests through the real stack: Keycloak, the Spring Boot API, PostgreSQL and S3 storage.
 * Start them first (docker compose up -d, then the backend); Playwright starts the frontend itself.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
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
  webServer: {
    // CI tests the production build; locally the dev server is quicker to start and is reused if running
    command: process.env.CI ? 'npm run build && npm run preview' : 'npm run dev',
    url: 'http://localhost:5180',
    reuseExistingServer: !process.env.CI,
  },
})
