import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../api/http'

export type Me = {
  id: string
  name: string
  email: string
  roles: string[]
  /** Signed in with a temporary password from the bank: nothing else works until it is replaced */
  mustChangePassword: boolean
}

export type ChangePassword = { currentPassword: string; newPassword: string }

export const STAFF_ROLES = ['ANALYST', 'MANAGER', 'ADMIN'] as const
export const isStaff = (me: Me) => me.roles.some(role => (STAFF_ROLES as readonly string[]).includes(role))
export const hasRole = (me: Me, role: string) => me.roles.includes(role)

/** Where a user starts: their businesses, or the bank's back office. */
export const homeOf = (me: Me) => (isStaff(me) ? '/bank' : '/businesses')

export type SignIn = { email: string; password: string }
export type SignUp = { fullName: string; email: string; password: string }

export const sessionKey = ['session'] as const

/** Who is signed in, or null if nobody is. Asked once and kept until signing in or out changes it. */
export function useSession() {
  return useQuery({
    queryKey: sessionKey,
    queryFn: async () => {
      try {
        return await api<Me>('/api/v1/me')
      } catch (error) {
        if (error instanceof ApiError && error.problem.status === 401) return null
        throw error
      }
    },
    staleTime: Infinity,
  })
}

function useStartSession<T>(path: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: T) => api<Me>(path, { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: me => {
      // Nothing cached for a previous user may show for this one
      queryClient.removeQueries({ predicate: query => query.queryKey[0] !== sessionKey[0] })
      queryClient.setQueryData(sessionKey, me)
    },
  })
}

export const useSignIn = () => useStartSession<SignIn>('/api/v1/auth/sign-in')
export const useChangePassword = () => useStartSession<ChangePassword>('/api/v1/me/password')
export const useSignUp = () => useStartSession<SignUp>('/api/v1/auth/sign-up')

/** Signs out here; with everywhere, also ends the user's sessions on every other device. */
export function useSignOut(everywhere = false) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>(everywhere ? '/api/v1/me/sign-out-everywhere' : '/api/v1/auth/sign-out', { method: 'POST' }),
    onSettled: () => {
      queryClient.clear()
      queryClient.setQueryData(sessionKey, null)
    },
  })
}
