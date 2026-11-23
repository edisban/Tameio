import { CssBaseline, ThemeProvider } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { Notifications } from '../app/Notifications'
import { theme } from '../app/theme'

type Options = {
  /** The address the app is at, e.g. "/businesses/42". */
  path?: string
  /** The route pattern that reads it, e.g. "/businesses/:businessId". */
  route?: string
}

/** Renders a component with the same providers the app uses, and a fresh cache so tests never share data. */
export function renderWithProviders(ui: ReactElement, { path = '/', route = '*' }: Options = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const user = userEvent.setup()
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path={route} element={ui} />
          </Routes>
        </MemoryRouter>
        <Notifications />
      </ThemeProvider>
    </QueryClientProvider>,
  )
  return { user, queryClient, ...result }
}
