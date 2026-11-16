import { Alert, Box, CircularProgress } from '@mui/material'
import { useEffect, type ReactNode } from 'react'
import { hasAuthParams, useAuth } from 'react-oidc-context'

/** Shows its children only to a signed-in user; anyone else is sent to Keycloak's sign-in page. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const mustSignIn = !hasAuthParams() && !auth.isAuthenticated && !auth.activeNavigator && !auth.isLoading && !auth.error

  useEffect(() => {
    if (mustSignIn) void auth.signinRedirect()
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
