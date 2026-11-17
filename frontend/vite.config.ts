import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5180,
    strictPort: true, // Keycloak only accepts redirects back to this exact port
    // The browser calls /api on the same origin; Vite forwards it to Spring Boot, so no CORS is needed
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  // The production build served locally (used by the e2e tests in CI); it reuses the /api proxy above
  preview: {
    port: 5180,
    strictPort: true,
  },
  test: {
    // Components render into a simulated browser; the API is answered by MSW in each test
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/test/**', 'src/main.tsx', 'src/vite-env.d.ts', 'src/**/*.test.{ts,tsx}'],
      reporter: ['text-summary', 'html'],
    },
  },
})
