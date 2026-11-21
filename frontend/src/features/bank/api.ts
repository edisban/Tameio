import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '../../api/http'

export type StaffRole = 'ANALYST' | 'MANAGER' | 'ADMIN'

export type StaffMember = {
  id: string
  email: string
  fullName: string
  role: StaffRole
  approvalLimit: number | null
  active: boolean
  mustChangePassword: boolean
  locked: boolean
  createdAt: string
}

export type WithTemporaryPassword = { user: StaffMember; temporaryPassword: string }
export type CreateStaff = { email: string; fullName: string; role: StaffRole; approvalLimit: number | null }
export type UpdateStaff = { role: StaffRole; approvalLimit: number | null; active: boolean }

export const roleLabels: Record<StaffRole, string> = {
  ANALYST: 'Αναλυτής πιστοδοτήσεων',
  MANAGER: 'Διευθυντής πιστοδοτήσεων',
  ADMIN: 'Διαχειριστής',
}

const base = '/api/v1/admin/staff'
const key = ['staff'] as const

export function useStaff() {
  return useQuery({ queryKey: key, queryFn: () => api<Page<StaffMember>>(`${base}?size=100`) })
}

export function useCreateStaff() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateStaff) => api<WithTemporaryPassword>(base, { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: ({ user }) => {
      // Shown at once from the answer, then confirmed by a fresh read
      queryClient.setQueryData<Page<StaffMember>>(key, page =>
        page ? { ...page, content: [...page.content.filter(m => m.id !== user.id), user] } : page,
      )
      return queryClient.invalidateQueries({ queryKey: key })
    },
  })
}

export function useUpdateStaff() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...input }: UpdateStaff & { id: string }) =>
      api<StaffMember>(`${base}/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  })
}

export function useResetPassword() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<WithTemporaryPassword>(`${base}/${id}/reset-password`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  })
}
