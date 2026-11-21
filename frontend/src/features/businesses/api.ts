import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../api/http'

export type Business = {
  id: string
  afm: string
  name: string
  role: 'OWNER' | 'ACCOUNTANT'
  createdAt: string
}

export type RegisterBusiness = { afm: string; name: string }

const keys = {
  all: ['businesses'] as const,
  one: (id: string) => ['businesses', id] as const,
}

export function useBusinesses() {
  return useQuery({ queryKey: keys.all, queryFn: () => api<Business[]>('/api/v1/businesses') })
}

export function useBusiness(id: string) {
  return useQuery({ queryKey: keys.one(id), queryFn: () => api<Business>(`/api/v1/businesses/${id}`) })
}

export function useRegisterBusiness() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: RegisterBusiness) =>
      api<Business>('/api/v1/businesses', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: created => {
      // Shown at once from the answer itself, then confirmed by a fresh read: the list never depends on
      // the timing of a refetch that may still be racing an earlier one
      queryClient.setQueryData<Business[]>(keys.all, list => (list ? [...list.filter(b => b.id !== created.id), created] : [created]))
      return queryClient.invalidateQueries({ queryKey: keys.all })
    },
  })
}
