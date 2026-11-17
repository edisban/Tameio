import { User, WebStorageStateStore } from 'oidc-client-ts'
import type { AuthProviderProps } from 'react-oidc-context'

const authority = import.meta.env.VITE_OIDC_AUTHORITY
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID

// Fail loudly at start-up: without these the app would wait forever for a sign-in that never starts
if (!authority || !clientId) {
  throw new Error('VITE_OIDC_AUTHORITY and VITE_OIDC_CLIENT_ID must be set at build time (see frontend/.env)')
}

/**
 * Sign-in with Keycloak using the authorization code flow with PKCE, the recommended flow for
 * browser apps. Tokens stay in sessionStorage, so they end with the tab rather than lingering.
 */
export const oidcConfig: AuthProviderProps = {
  authority,
  client_id: clientId,
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: true,
  // After signing in, drop ?code=…&state=… from the address bar
  onSigninCallback: () => window.history.replaceState({}, document.title, window.location.pathname),
}

/** The current access token, for code outside React components (the API client). */
export function accessToken(): string | undefined {
  const stored = sessionStorage.getItem(`oidc.user:${authority}:${clientId}`)
  return stored ? User.fromStorageString(stored).access_token : undefined
}
