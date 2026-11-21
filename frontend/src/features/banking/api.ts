import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '../../api/http'

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED'
export type EntryKind = 'INCOMING_PAYMENT' | 'OUTGOING_PAYMENT' | 'LOAN_DISBURSEMENT'

export type BankAccount = {
  id: string
  iban: string
  name: string
  holderName: string
  currency: string
  balance: number
  status: AccountStatus
  statusReason: string | null
  openedAt: string
}

export type Transaction = {
  id: string
  entryId: string
  kind: EntryKind
  description: string
  reference: string | null
  amount: number
  balanceAfter: number
  counterpartyName: string | null
  counterpartyIban: string | null
  bookedAt: string
}

const STATEMENT_PAGE_SIZE = 15

const base = (businessId: string) => `/api/v1/businesses/${businessId}/accounts`
const keys = {
  all: (businessId: string) => ['banking', businessId] as const,
  statement: (businessId: string, accountId: string, page: number) => ['banking', businessId, accountId, page] as const,
}

export function useAccounts(businessId: string) {
  return useQuery({ queryKey: keys.all(businessId), queryFn: () => api<BankAccount[]>(base(businessId)) })
}

export function useStatement(businessId: string, accountId: string, page: number) {
  return useQuery({
    queryKey: keys.statement(businessId, accountId, page),
    queryFn: () => api<Page<Transaction>>(`${base(businessId)}/${accountId}/transactions?page=${page}&size=${STATEMENT_PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  })
}

/**
 * Opens an account. The new account goes into the list straight from the answer, so the page can select
 * it at once; waiting for a refetch first would let that late selection undo a click made meanwhile.
 */
export function useOpenAccount(businessId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => api<BankAccount>(base(businessId), { method: 'POST', body: JSON.stringify({ name }) }),
    onSuccess: account => {
      queryClient.setQueryData<BankAccount[]>(keys.all(businessId), list => (list ? [...list.filter(a => a.id !== account.id), account] : [account]))
      void queryClient.invalidateQueries({ queryKey: keys.all(businessId), exact: true })
    },
  })
}

export function useDemoDeposit(businessId: string, accountId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (amount: number) =>
      api<Transaction>(`${base(businessId)}/${accountId}/demo-deposits`, { method: 'POST', body: JSON.stringify({ amount }) }),
    // The balance and the statement change together
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all(businessId) }),
  })
}
