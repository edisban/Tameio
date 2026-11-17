import { Alert, Box, CircularProgress } from '@mui/material'
import { useEffect, useRef, type ReactNode } from 'react'
import { hasAuthParams, useAuth } from 'react-oidc-context'

/** Shows its children only to a signed-in user; anyone else is sent to Keycloak's sign-in page. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const mustSignIn = !hasAuthParams() && !auth.isAuthenticated && !auth.activeNavigator && !auth.isLoading && !auth.error
  // The auth object changes on every render; without this guard a second redirect could start and
  // abort the first, leaving the page waiting on a sign-in that never arrives
  const redirecting = useRef(false)

  useEffect(() => {
    if (mustSignIn && !redirecting.current) {
      redirecting.current = true
      void auth.signinRedirect()
    }
  }, [mustSignIn, auth])

  if (auth.error) {
    return <Alert severity="error" sx={{ m: 4 }}>Η σύνδεση απέτυχε: {auth.error.message}</Alert>
  }
  if (!auth.isAuthenticated) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <CircularProgress />
      </Box>
    )
  }
  return children
}
