import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '../../api/http'
import type { BankAccount } from '../banking/api'

export type TrialBalance = {
  internal: { code: string; name: string; balance: number }[]
  customerDeposits: number
  customerAccounts: number
  total: number
  balanced: boolean
  journalEntries: number
}

export const internalNames: Record<string, string> = {
  SETTLEMENT: 'Διακανονισμός με την Τράπεζα της Ελλάδος (TARGET2)',
  SEPA_OUT: 'Εκκαθάριση εξερχόμενων SEPA',
  SEPA_IN: 'Εκκαθάριση εισερχόμενων SEPA',
  LOANS: 'Δάνεια σε πελάτες',
  INTEREST_INCOME: 'Έσοδα από τόκους',
  FEE_INCOME: 'Έσοδα από προμήθειες',
}

export function useTrialBalance() {
  return useQuery({ queryKey: ['bank', 'trial-balance'], queryFn: () => api<TrialBalance>('/api/v1/bank/ledger/trial-balance') })
}

export function useCustomerAccounts(search: string, page: number) {
  return useQuery({
    queryKey: ['bank', 'accounts', search, page],
    queryFn: () => api<Page<BankAccount>>(`/api/v1/bank/accounts?search=${encodeURIComponent(search)}&page=${page}&size=25`),
    placeholderData: keepPreviousData,
  })
}

export function useFreeze() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string | null }) =>
      reason === null
        ? api<BankAccount>(`/api/v1/bank/accounts/${id}/unfreeze`, { method: 'POST' })
        : api<BankAccount>(`/api/v1/bank/accounts/${id}/freeze`, { method: 'POST', body: JSON.stringify({ reason }) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['bank', 'accounts'] }),
  })
}
