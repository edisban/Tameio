import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '../../api/http'

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED'
export type EntryKind = 'TRANSFER' | 'INCOMING_PAYMENT' | 'SEPA_PAYMENT' | 'SEPA_SETTLEMENT' | 'SEPA_RETURN'

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

export type TransferInput = {
  creditorIban: string
  creditorName: string
  amount: number
  reference: string | null
  /** The payer read that the name does not match the account, and pays anyway */
  confirmedMismatch?: boolean
}

export type PayeeCheckResult = 'MATCH' | 'CLOSE_MATCH' | 'NO_MATCH' | 'NOT_POSSIBLE'
export type PayeeCheck = { result: PayeeCheckResult; actualName: string | null }

export type PaymentStatus = 'COMPLETED' | 'PENDING' | 'SETTLED' | 'RETURNED'

export type Payment = {
  id: string
  direction: 'OUTGOING' | 'INCOMING'
  scheme: 'INTERNAL' | 'SEPA'
  status: PaymentStatus
  amount: number
  accountIban: string
  counterpartyIban: string
  counterpartyName: string
  reference: string | null
  endToEndId: string
  payeeCheck: PayeeCheckResult | null
  reasonCode: string | null
  reason: string | null
  createdAt: string
  balanceAfter: number | null
}

export type DirectoryAccount = { iban: string; holderName: string; bank: string }

const base = (businessId: string) => `/api/v1/businesses/${businessId}/accounts`
const keys = {
  all: (businessId: string) => ['banking', businessId] as const,
  statement: (businessId: string, accountId: string, page: number) => ['banking', businessId, accountId, page] as const,
  payments: (businessId: string, accountId: string) => ['banking', businessId, accountId, 'payments'] as const,
}

const POLL_MS = 1_500

export function useAccounts(businessId: string) {
  return useQuery({ queryKey: keys.all(businessId), queryFn: () => api<BankAccount[]>(base(businessId)) })
}

export function useStatement(businessId: string, accountId: string, page: number) {
  return useQuery({
    queryKey: keys.statement(businessId, accountId, page),
    queryFn: () => api<Page<Transaction>>(`${base(businessId)}/${accountId}/transactions?page=${page}&size=15`),
    placeholderData: keepPreviousData,
  })
}

/** The latest payments; while one is in clearing, ask again until it settles or comes back. */
export function usePayments(businessId: string, accountId: string) {
  const queryClient = useQueryClient()
  return useQuery({
    queryKey: keys.payments(businessId, accountId),
    queryFn: async () => {
      const page = await api<Page<Payment>>(`${base(businessId)}/${accountId}/payments?size=8`)
      const previous = queryClient.getQueryData<Page<Payment>>(keys.payments(businessId, accountId))
      const wasPending = previous?.content.some(p => p.status === 'PENDING')
      // A payment just left clearing: a return changes the balance and the statement
      if (wasPending && !page.content.some(p => p.status === 'PENDING')) {
        void queryClient.invalidateQueries({ queryKey: keys.all(businessId), predicate: q => q.queryKey[3] !== 'payments' })
      }
      return page
    },
    refetchInterval: query => (query.state.data?.content.some(p => p.status === 'PENDING') ? POLL_MS : false),
  })
}

export function useVerifyPayee() {
  return useMutation({
    mutationFn: (input: { iban: string; name: string }) =>
      api<PayeeCheck>('/api/v1/payee-verification', { method: 'POST', body: JSON.stringify(input) }),
  })
}

/** Accounts at the simulated other banks, offered as payees in demos. */
export function useDirectory() {
  return useQuery({
    queryKey: ['clearing', 'directory'],
    queryFn: () => api<DirectoryAccount[]>('/api/v1/clearing/directory'),
    staleTime: Infinity,
  })
}

function useBankingMutation<I, O>(businessId: string, request: (input: I) => Promise<O>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: request,
    // Balances and statements change together; the accounting overview reads them too
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all(businessId) }),
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

export const useDemoDeposit = (businessId: string, accountId: string) =>
  useBankingMutation(businessId, (amount: number) =>
    api<Transaction>(`${base(businessId)}/${accountId}/demo-deposits`, { method: 'POST', body: JSON.stringify({ amount }) }),
  )

/**
 * Sends a transfer with an Idempotency-Key the caller keeps for this one payment. If the answer is lost
 * and the person presses again, the server recognises the key and the money moves only once.
 */
export const useTransfer = (businessId: string, accountId: string) =>
  useBankingMutation(businessId, ({ key, ...input }: TransferInput & { key: string }) =>
    api<Payment>(`${base(businessId)}/${accountId}/transfers`, {
      method: 'POST',
      body: JSON.stringify(input),
      headers: { 'Idempotency-Key': key },
    }),
  )
