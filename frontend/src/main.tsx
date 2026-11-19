import { CssBaseline, ThemeProvider } from '@mui/material'
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { Layout } from './app/Layout'
import { theme } from './app/theme'
import { ApiError } from './api/http'
import { RequireSession } from './auth/RequireSession'
import { sessionKey } from './auth/session'
import { SignInPage } from './auth/SignInPage'
import { SignUpPage } from './auth/SignUpPage'
import { BusinessesPage } from './features/businesses/BusinessesPage'
import { BusinessDocumentsPage } from './features/documents/BusinessDocumentsPage'

// When the session runs out, any call answers 401: forget the user, and RequireSession sends them to sign in
function onError(error: Error) {
  if (error instanceof ApiError && error.problem.status === 401) {
    queryClient.setQueryData(sessionKey, null)
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError }),
  mutationCache: new MutationCache({ onError }),
  defaultOptions: {
    queries: {
      // Retry network hiccups, not answers like 403 or 404 that won't change on a retry
      retry: (failureCount, error) => !(error instanceof ApiError && error.problem.status < 500) && failureCount < 2,
    },
  },
})

const router = createBrowserRouter([
  { path: '/sign-in', element: <SignInPage /> },
  { path: '/sign-up', element: <SignUpPage /> },
  {
    element: (
      <RequireSession>
        <Layout />
      </RequireSession>
    ),
    children: [
      { index: true, element: <Navigate to="/businesses" replace /> },
      { path: 'businesses', element: <BusinessesPage /> },
      { path: 'businesses/:businessId', element: <BusinessDocumentsPage /> },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <RouterProvider router={router} />
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
)
