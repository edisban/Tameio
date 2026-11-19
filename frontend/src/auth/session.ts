import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../api/http'

export type Me = {
  id: string
  name: string
  email: string
  roles: string[]
}

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
export const useSignUp = () => useStartSession<SignUp>('/api/v1/auth/sign-up')

export function useSignOut() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/v1/auth/sign-out', { method: 'POST' }),
    onSettled: () => {
      queryClient.clear()
      queryClient.setQueryData(sessionKey, null)
    },
  })
}
