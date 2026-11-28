import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import type { Case, QueueItem } from './api'
import { LoanCasePage } from './LoanCasePage'
import { LoanQueuePage } from './LoanQueuePage'

const analyst = { id: 's1', name: 'Daniel Weber', email: 'analyst@bank.example', roles: ['ANALYST'], mustChangePassword: false }
const manager = { id: 's2', name: 'Sofia Lindqvist', email: 'manager@bank.example', roles: ['MANAGER'], mustChangePassword: false }

const item: QueueItem = {
  id: 'l1', businessName: 'Φούρνος Η Ζύμη', amount: 40000, termMonths: 60, purpose: 'EQUIPMENT', status: 'SUBMITTED',
  riskScore: 68, riskBand: 'MEDIUM', submittedAt: '2026-11-20T10:00:00Z',
}

const submitted: Case = {
  id: 'l1', businessName: 'Φούρνος Η Ζύμη', amount: 40000, termMonths: 60, purpose: 'EQUIPMENT', description: 'Δεύτερος φούρνος',
  annualRate: 0.07, monthlyInstalment: 792.05, status: 'SUBMITTED',
  figures: { averageMonthlyInflow: 14000, averageMonthlyOutflow: 11000, averageMonthlyExpenses: 10500, balance: 9000, monthsOfHistory: 12 },
  riskScore: 68, riskBand: 'MEDIUM',
  riskFactors: [
    { code: 'REPAYMENT_CAPACITY', measured: 0.264, points: 18, maxPoints: 25 },
    { code: 'TRACK_RECORD', measured: 12, points: 25, maxPoints: 25 },
    { code: 'CASH_CUSHION', measured: 11.4, points: 25, maxPoints: 25 },
    { code: 'LOAN_SIZE', measured: 0.238, points: 0, maxPoints: 25 },
  ],
  submittedAt: '2026-11-20T10:00:00Z', review: null, recommendation: null, recommendationNote: null, recommended: null,
  decisionNote: null, decided: null, disbursedAt: null,
  credit: {
    defaultProbability: 0.0812, modelVersion: '1',
    factors: [
      { measure: 'repayment_ratio', value: 0.264, contribution: -0.42 },
      { measure: 'loan_size', value: 0.238, contribution: -0.11 },
      { measure: 'history_months', value: 12, contribution: 0.05 },
      { measure: 'cash_cushion', value: 11.4, contribution: -0.3 },
      { measure: 'inflow_volatility', value: 0.31, contribution: 0.18 },
      { measure: 'negative_months', value: 0.25, contribution: 0.44 },
    ],
  },
}

const recommended: Case = {
  ...submitted, status: 'RECOMMENDED',
  review: { id: 's1', name: 'Daniel Weber', at: '2026-11-20T11:00:00Z' },
  recommendation: 'APPROVE', recommendationNote: 'Σταθερές εισροές.',
  recommended: { id: 's1', name: 'Daniel Weber', at: '2026-11-20T12:00:00Z' },
}

const page = <T,>(content: T[]) => ({ content, page: { size: 25, number: 0, totalElements: content.length, totalPages: 1 } })

const renderCase = () => renderWithProviders(<LoanCasePage />, { path: '/bank/loans/l1', route: '/bank/loans/:id' })

describe('LoanQueuePage', () => {
  it('lists applications with their risk and status, and filters by status', async () => {
    let asked = ''
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(analyst)),
      http.get('*/api/v1/bank/loan-applications', ({ request }) => {
        asked = new URL(request.url).searchParams.get('status') ?? ''
        return HttpResponse.json(page(asked === 'DECLINED' ? [] : [item]))
      }),
    )
    const { user } = renderWithProviders(<LoanQueuePage />)

    const table = within(await screen.findByRole('table', { name: 'Αιτήσεις δανείων' }))
    const row = (await table.findByRole('link', { name: 'Φούρνος Η Ζύμη' })).closest('tr')!
    expect(within(row).getByText('68 · Μέτριος κίνδυνος')).toBeInTheDocument()
    expect(within(row).getByText('Υποβλήθηκε')).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: 'Απορρίφθηκε' }))

    expect(await table.findByText('Καμία αίτηση σε αυτή την κατάσταση.')).toBeInTheDocument()
    expect(asked).toBe('DECLINED')
  })
})

describe('LoanQueuePage paging', () => {
  it('asks the bank for the next page of applications', async () => {
    const pages: string[] = []
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(analyst)),
      http.get('*/api/v1/bank/loan-applications', ({ request }) => {
        const number = Number(new URL(request.url).searchParams.get('page'))
        pages.push(String(number))
        const name = number === 0 ? 'Φούρνος Η Ζύμη' : 'Ταβέρνα Η Αυλή'
        return HttpResponse.json({
          content: [{ ...item, id: `l${number}`, businessName: name }],
          page: { size: 25, number, totalElements: 26, totalPages: 2 },
        })
      }),
    )
    const { user } = renderWithProviders(<LoanQueuePage />)
    await screen.findByRole('link', { name: 'Φούρνος Η Ζύμη' })

    await user.click(screen.getByRole('button', { name: /next page/i }))

    expect(await screen.findByRole('link', { name: 'Ταβέρνα Η Αυλή' })).toBeInTheDocument()
    expect(pages).toEqual(['0', '1'])
  })
})

describe('LoanCasePage', () => {
  it('explains each risk factor with what was measured and the points it earned', async () => {
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(analyst)),
      http.get('*/api/v1/bank/loan-applications/l1', () => HttpResponse.json(submitted)),
    )
    renderCase()

    expect(await screen.findByLabelText('Βαθμός κινδύνου 68 από 100')).toBeInTheDocument()
    const factors = within(screen.getByRole('list', { name: 'Παράγοντες κινδύνου' }))
    expect(factors.getByText('Η δόση είναι το 26% του μηνιαίου πλεονάσματος.')).toBeInTheDocument()
    expect(factors.getByText('18/25')).toBeInTheDocument()
  })

  it('shows the credit model estimate beside the score, and which way each measure moved it', async () => {
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(analyst)),
      http.get('*/api/v1/bank/loan-applications/l1', () => HttpResponse.json(submitted)),
    )
    renderCase()

    expect(await screen.findByLabelText('Πιθανότητα αθέτησης 8,1%')).toBeInTheDocument()
    expect(screen.getByText(/Εκτίμηση μοντέλου · έκδοση 1/)).toBeInTheDocument()
    const moved = within(screen.getByRole('list', { name: 'Τι μετακίνησε την εκτίμηση' }))
    const red = moved.getByText('Μήνες με περισσότερα έξοδα από έσοδα').closest('li')!
    expect(within(red).getByText('Ανεβάζει την εκτίμηση')).toBeInTheDocument()
    expect(within(red).getByText('25%')).toBeInTheDocument()
    const blue = moved.getByText('Δόση προς μηνιαίο πλεόνασμα').closest('li')!
    expect(within(blue).getByText('Κατεβάζει την εκτίμηση')).toBeInTheDocument()
  })

  it('leaves the estimate out for an application assessed before the model', async () => {
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(analyst)),
      http.get('*/api/v1/bank/loan-applications/l1', () => HttpResponse.json({ ...submitted, credit: null })),
    )
    renderCase()

    expect(await screen.findByLabelText('Βαθμός κινδύνου 68 από 100')).toBeInTheDocument()
    expect(screen.queryByText(/Εκτίμηση μοντέλου/)).not.toBeInTheDocument()
  })

  it('lets an analyst take a submitted case and then recommend it with a reason', async () => {
    let current = submitted
    let verdict: unknown
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(analyst)),
      http.get('*/api/v1/bank/loan-applications/l1', () => HttpResponse.json(current)),
      http.get('*/api/v1/bank/loan-applications', () => HttpResponse.json(page([item]))),
      http.post('*/api/v1/bank/loan-applications/l1/review', () => {
        current = { ...submitted, status: 'IN_REVIEW', review: recommended.review }
        return HttpResponse.json(current)
      }),
      http.post('*/api/v1/bank/loan-applications/l1/recommendation', async ({ request }) => {
        verdict = await request.json()
        current = recommended
        return HttpResponse.json(current)
      }),
    )
    const { user } = renderCase()

    await user.click(await screen.findByRole('button', { name: 'Ανάληψη αξιολόγησης' }))
    const send = await screen.findByRole('button', { name: 'Υποβολή εισήγησης' })
    expect(send).toBeDisabled()
    await user.type(screen.getByLabelText('Αιτιολόγηση'), 'Σταθερές εισροές.')
    await user.click(send)

    expect(await screen.findByText('Εισήγηση: έγκριση')).toBeInTheDocument()
    expect(verdict).toEqual({ outcome: 'APPROVE', note: 'Σταθερές εισροές.' })
    expect(screen.queryByRole('button', { name: 'Οριστική απόφαση' })).not.toBeInTheDocument()
  })

  it('shows a manager why the bank refused their decision', async () => {
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(manager)),
      http.get('*/api/v1/bank/loan-applications/l1', () => HttpResponse.json(recommended)),
      http.post('*/api/v1/bank/loan-applications/l1/decision', () =>
        HttpResponse.json(
          { status: 403, title: 'Not allowed', detail: 'Your approval limit does not cover this amount.', code: 'above-approval-limit' },
          { status: 403, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const { user } = renderCase()

    await user.type(await screen.findByLabelText('Αιτιολόγηση'), 'Καλό προφίλ.')
    await user.click(screen.getByRole('button', { name: 'Οριστική απόφαση' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Το ποσό ξεπερνά το όριο έγκρισής σας.')
  })
})
