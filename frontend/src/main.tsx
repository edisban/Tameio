import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { Layout } from './app/Layout'
import { theme } from './app/theme'
import { oidcConfig } from './auth/oidc'
import { RequireAuth } from './auth/RequireAuth'
import { ApiError } from './api/http'
import { BusinessesPage } from './features/businesses/BusinessesPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Retry network hiccups, not answers like 403 or 404 that won't change on a retry
      retry: (failureCount, error) => !(error instanceof ApiError && error.problem.status < 500) && failureCount < 2,
    },
  },
})

const router = createBrowserRouter([
  {
    element: (
      <RequireAuth>
        <Layout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/businesses" replace /> },
      { path: 'businesses', element: <BusinessesPage /> },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <RouterProvider router={router} />
        </ThemeProvider>
      </QueryClientProvider>
    </AuthProvider>
  </StrictMode>,
)
