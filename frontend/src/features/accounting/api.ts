import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api, apiBlob, type Page } from '../../api/http'

export type Totals = { net: number; vat: number; total: number; count: number }
export type Period = { number: number; totals: Totals }

export type YearSummary = {
  year: number
  totals: Totals
  months: Period[]
  quarters: Period[]
  categories: { category: string; totals: Totals }[]
}

export type Expense = {
  id: string
  documentId: string
  supplierAfm: string
  issueDate: string
  netAmount: number
  vatAmount: number
  totalAmount: number
  category: string
}

const base = (businessId: string) => `/api/v1/businesses/${businessId}`

export function useYearSummary(businessId: string, year: number) {
  return useQuery({
    queryKey: ['accounting', businessId, 'summary', year],
    queryFn: () => api<YearSummary>(`${base(businessId)}/accounting/summary?year=${year}`),
    placeholderData: keepPreviousData,
  })
}

export function useExpenses(businessId: string, year: number, category: string, page: number) {
  const params = new URLSearchParams({ from: `${year}-01-01`, to: `${year}-12-31`, page: String(page), size: '20' })
  if (category) params.set('category', category)
  return useQuery({
    queryKey: ['accounting', businessId, 'expenses', year, category, page],
    queryFn: () => api<Page<Expense>>(`${base(businessId)}/expenses?${params}`),
    placeholderData: keepPreviousData,
  })
}

/** Downloads the year's expense book as a CSV file for Excel. */
export async function downloadExpenses(businessId: string, year: number) {
  const blob = await apiBlob(`${base(businessId)}/expenses/export?from=${year}-01-01&to=${year}-12-31`)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `exoda-${year}.csv`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
