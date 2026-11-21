import { Navigate } from 'react-router'
import { homeOf, useSession } from './session'

/** The app's front door: business owners go to their businesses, bank staff to the back office. */
export function HomeRedirect() {
  const session = useSession()
  return session.data ? <Navigate to={homeOf(session.data)} replace /> : null
}
