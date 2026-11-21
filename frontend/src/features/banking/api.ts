import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '../../api/http'

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED'
export type EntryKind = 'TRANSFER' | 'INCOMING_PAYMENT'

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

export type TransferInput = { creditorIban: string; creditorName: string; amount: number; reference: string | null }

export type TransferResult = {
  id: string
  status: string
  amount: number
  debtorIban: string
  creditorIban: string
  creditorName: string
  reference: string | null
  bookedAt: string
  balanceAfter: number
}

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
    queryFn: () => api<Page<Transaction>>(`${base(businessId)}/${accountId}/transactions?page=${page}&size=15`),
    placeholderData: keepPreviousData,
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

export const useOpenAccount = (businessId: string) =>
  useBankingMutation(businessId, (name: string) => api<BankAccount>(base(businessId), { method: 'POST', body: JSON.stringify({ name }) }))

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
    api<TransferResult>(`${base(businessId)}/${accountId}/transfers`, {
      method: 'POST',
      body: JSON.stringify(input),
      headers: { 'Idempotency-Key': key },
    }),
  )
