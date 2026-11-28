import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '../../api/http'

export type LoanStatus = 'DRAFT' | 'SUBMITTED' | 'IN_REVIEW' | 'RECOMMENDED' | 'APPROVED' | 'DECLINED' | 'DISBURSED'
export type LoanPurpose = 'WORKING_CAPITAL' | 'EQUIPMENT' | 'RENOVATION' | 'EXPANSION'
export type Outcome = 'APPROVE' | 'DECLINE'
export type RiskBand = 'LOW' | 'MEDIUM' | 'HIGH'
export type RiskCode = 'REPAYMENT_CAPACITY' | 'TRACK_RECORD' | 'CASH_CUSHION' | 'LOAN_SIZE'

export type Figures = {
  averageMonthlyInflow: number
  averageMonthlyOutflow: number
  averageMonthlyExpenses: number
  balance: number
  monthsOfHistory: number
}

export type LoanTerms = {
  accountId: string
  amount: number
  termMonths: number
  purpose: LoanPurpose
  description: string | null
}

/** A disbursed loan: the next instalment (none once all are due) and how many are due now. */
export type Repayment = { nextDueOn: string | null; nextAmount: number | null; instalmentsDue: number }

/** An application as the business sees it. */
export type Application = {
  id: string
  accountId: string
  amount: number
  termMonths: number
  purpose: LoanPurpose
  description: string | null
  annualRate: number
  monthlyInstalment: number
  status: LoanStatus
  figures: Figures
  createdAt: string
  submittedAt: string | null
  decisionNote: string | null
  decidedAt: string | null
  disbursedAt: string | null
  repayment: Repayment | null
}

export type QueueItem = {
  id: string
  businessName: string
  amount: number
  termMonths: number
  purpose: LoanPurpose
  status: LoanStatus
  riskScore: number
  riskBand: RiskBand
  submittedAt: string
}

export type RiskFactor = { code: RiskCode; measured: number | null; points: number; maxPoints: number }
export type Step = { id: string; name: string; at: string }

export type CreditMeasure =
  | 'repayment_ratio'
  | 'loan_size'
  | 'history_months'
  | 'cash_cushion'
  | 'inflow_volatility'
  | 'negative_months'

/** The credit model's estimate beside the scorecard; null for applications assessed before it existed. */
export type CreditEstimate = {
  defaultProbability: number
  modelVersion: string
  factors: { measure: CreditMeasure; value: number; contribution: number }[]
}

/** The whole case, as the bank sees it. */
export type Case = {
  id: string
  businessName: string
  amount: number
  termMonths: number
  purpose: LoanPurpose
  description: string | null
  annualRate: number
  monthlyInstalment: number
  status: LoanStatus
  figures: Figures
  riskScore: number
  riskBand: RiskBand
  riskFactors: RiskFactor[]
  submittedAt: string
  review: Step | null
  recommendation: Outcome | null
  recommendationNote: string | null
  recommended: Step | null
  decisionNote: string | null
  decided: Step | null
  disbursedAt: string | null
  credit: CreditEstimate | null
}

export type Verdict = { outcome: Outcome; note: string }

const QUEUE_PAGE_SIZE = 25

const businessBase = (businessId: string) => `/api/v1/businesses/${businessId}/loan-applications`
const bankBase = '/api/v1/bank/loan-applications'

const keys = {
  business: (businessId: string) => ['loans', 'business', businessId] as const,
  queue: (status: LoanStatus | '', page: number) => ['loans', 'queue', status, page] as const,
  case: (id: string) => ['loans', 'case', id] as const,
}

export function useApplications(businessId: string) {
  return useQuery({ queryKey: keys.business(businessId), queryFn: () => api<Application[]>(businessBase(businessId)) })
}

export function useDraftApplication(businessId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (terms: LoanTerms) => api<Application>(businessBase(businessId), { method: 'POST', body: JSON.stringify(terms) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.business(businessId) }),
  })
}

export function useSubmitApplication(businessId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<Application>(`${businessBase(businessId)}/${id}/submit`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.business(businessId) }),
  })
}

export function useQueue(status: LoanStatus | '', page: number) {
  const params = new URLSearchParams({ page: String(page), size: String(QUEUE_PAGE_SIZE) })
  if (status) params.set('status', status)
  return useQuery({
    queryKey: keys.queue(status, page),
    queryFn: () => api<Page<QueueItem>>(`${bankBase}?${params}`),
    placeholderData: keepPreviousData,
  })
}

export function useCase(id: string) {
  return useQuery({ queryKey: keys.case(id), queryFn: () => api<Case>(`${bankBase}/${id}`) })
}

/** A review step on a case: the answer is the updated case, which replaces the cached one. */
function useCaseAction<I>(id: string, path: string, body?: (input: I) => unknown) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: I) =>
      api<Case>(`${bankBase}/${id}/${path}`, { method: 'POST', body: body ? JSON.stringify(body(input)) : undefined }),
    onSuccess: updated => {
      queryClient.setQueryData(keys.case(id), updated)
      return queryClient.invalidateQueries({ queryKey: ['loans', 'queue'] })
    },
  })
}

export const useStartReview = (id: string) => useCaseAction<void>(id, 'review')
export const useRecommend = (id: string) => useCaseAction<Verdict>(id, 'recommendation', verdict => verdict)
export const useDecide = (id: string) => useCaseAction<Verdict>(id, 'decision', verdict => verdict)
export const useDisburse = (id: string) => useCaseAction<void>(id, 'disbursement')
