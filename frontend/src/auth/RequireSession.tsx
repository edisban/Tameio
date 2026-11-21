import { Alert, Box, CircularProgress } from '@mui/material'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { LowPolyBackground } from '../app/LowPolyBackground'
import { useSession } from './session'

/** Shows its children only to a signed-in user; anyone else goes to the sign-in page and comes back after. */
export function RequireSession({ children }: { children: ReactNode }) {
  const session = useSession()
  const location = useLocation()

  if (session.isPending) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <LowPolyBackground />
        <CircularProgress aria-label="Φόρτωση" sx={{ color: '#ffffff', zIndex: 1 }} />
      </Box>
    )
  }
  if (session.isError) {
    return (
      <Alert severity="error" sx={{ m: 4 }}>
        Δεν ήταν δυνατή η σύνδεση με τον διακομιστή. Ανανεώστε τη σελίδα.
      </Alert>
    )
  }
  if (!session.data) {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />
  }
  if (session.data.mustChangePassword) {
    return <Navigate to="/change-password" replace />
  }
  return children
}
