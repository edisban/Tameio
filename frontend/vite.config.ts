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
  test: {
    environment: 'node',
  },
})
