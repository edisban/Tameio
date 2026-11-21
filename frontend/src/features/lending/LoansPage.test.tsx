import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import type { BankAccount } from '../banking/api'
import type { Application } from './api'
import { LoansPage } from './LoansPage'

const business = { id: 'b1', afm: '094014201', name: 'Καφέ Το Στέκι', role: 'OWNER', createdAt: '2026-11-17T10:00:00Z' }

const account: BankAccount = {
  id: 'a1', iban: 'GR3699900010000000001000001', name: 'Λογαριασμός όψεως', holderName: 'Καφέ Το Στέκι', currency: 'EUR',
  balance: 12500, status: 'ACTIVE', statusReason: null, openedAt: '2026-01-20T10:00:00Z',
}

const draft: Application = {
  id: 'l1', accountId: 'a1', amount: 10000, termMonths: 36, purpose: 'EQUIPMENT', description: 'Νέα μηχανή espresso',
  annualRate: 0.07, monthlyInstalment: 308.77, status: 'DRAFT',
  figures: { averageMonthlyInflow: 9000, averageMonthlyOutflow: 7000, averageMonthlyExpenses: 6500, balance: 12500, monthsOfHistory: 10 },
  createdAt: '2026-11-20T10:00:00Z', submittedAt: null, decisionNote: null, decidedAt: null, disbursedAt: null,
  repayment: null,
}

function serve(applications: () => Application[], accounts: BankAccount[] = [account]) {
  server.use(
    http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
    http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json(accounts)),
    http.get('*/api/v1/businesses/b1/loan-applications', () => HttpResponse.json(applications())),
  )
}

const renderPage = () => renderWithProviders(<LoansPage />, { path: '/businesses/b1/loans', route: '/businesses/:businessId/loans' })

describe('LoansPage', () => {
  it('asks for an account before a business can apply', async () => {
    serve(() => [], [])
    renderPage()

    expect(await screen.findByText('Για δάνειο χρειάζεστε πρώτα λογαριασμό στο Tameio.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Νέα αίτηση' })).toBeDisabled()
  })

  it('refuses an amount or a term outside the product limits', async () => {
    serve(() => [])
    const { user } = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Νέα αίτηση' }))

    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText('Ποσό (€)'), '500')
    await user.clear(dialog.getByLabelText('Διάρκεια (μήνες)'))
    await user.type(dialog.getByLabelText('Διάρκεια (μήνες)'), '120')
    await user.click(dialog.getByRole('button', { name: 'Αποθήκευση πρόχειρης' }))

    expect(await dialog.findByText('Από 1.000 έως 500.000 €')).toBeInTheDocument()
    expect(dialog.getByText('Από 6 έως 84 μήνες')).toBeInTheDocument()
  })

  it('saves a draft with the terms the business typed', async () => {
    let sent: unknown
    let applications: Application[] = []
    serve(() => applications)
    server.use(
      http.post('*/api/v1/businesses/b1/loan-applications', async ({ request }) => {
        sent = await request.json()
        applications = [draft]
        return HttpResponse.json(draft, { status: 201 })
      }),
    )
    const { user } = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Νέα αίτηση' }))

    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText('Ποσό (€)'), '10.000')
    await user.type(dialog.getByLabelText('Περιγραφή'), 'Νέα μηχανή espresso')
    await user.click(dialog.getByRole('button', { name: 'Αποθήκευση πρόχειρης' }))

    expect(await screen.findByRole('button', { name: 'Υποβολή στην τράπεζα' })).toBeInTheDocument()
    expect(sent).toEqual({ accountId: 'a1', amount: 10000, termMonths: 36, purpose: 'EQUIPMENT', description: 'Νέα μηχανή espresso' })
  })

  it('submits a draft to the bank and shows it as submitted', async () => {
    let applications = [draft]
    serve(() => applications)
    server.use(
      http.post('*/api/v1/businesses/b1/loan-applications/l1/submit', () => {
        applications = [{ ...draft, status: 'SUBMITTED', submittedAt: '2026-11-21T09:00:00Z' }]
        return HttpResponse.json(applications[0])
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Υποβολή στην τράπεζα' }))

    expect(await screen.findAllByText('Υποβλήθηκε')).not.toHaveLength(0)
    expect(screen.queryByRole('button', { name: 'Υποβολή στην τράπεζα' })).not.toBeInTheDocument()
  })

  it('shows a paid-out loan with its next instalment and the ones that are due', async () => {
    serve(() => [{
      ...draft, status: 'DISBURSED', submittedAt: '2026-11-20T11:00:00Z', decidedAt: '2026-11-20T12:00:00Z',
      disbursedAt: '2026-11-20T13:00:00Z', repayment: { nextDueOn: '2026-12-20', nextAmount: 308.77, instalmentsDue: 2 },
    }])
    renderPage()

    expect(await screen.findByText('2 δόσεις είναι πληρωτέες.')).toBeInTheDocument()
    expect(screen.getByText(/Επόμενη δόση 308,77\s€ στις 20 Δεκεμβρίου 2026\./)).toBeInTheDocument()
  })
})
