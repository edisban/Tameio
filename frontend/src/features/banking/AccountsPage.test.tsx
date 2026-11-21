import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import { AccountsPage } from './AccountsPage'
import type { BankAccount, Transaction } from './api'

const business = { id: 'b1', afm: '094014201', name: 'Καφέ Το Στέκι', role: 'OWNER', createdAt: '2026-11-17T10:00:00Z' }

const account: BankAccount = {
  id: 'a1', iban: 'GR3699900010000000001000001', name: 'Λογαριασμός όψεως', holderName: 'Καφέ Το Στέκι', currency: 'EUR',
  balance: 1250.5, status: 'ACTIVE', statusReason: null, openedAt: '2026-11-20T10:00:00Z',
}

const income: Transaction = {
  id: 't1', entryId: 'e1', kind: 'INCOMING_PAYMENT', description: 'Incoming payment', reference: 'Demo', amount: 1250.5,
  balanceAfter: 1250.5, counterpartyName: 'Πελάτης Demo Α.Ε.', counterpartyIban: 'GR96…', bookedAt: '2026-11-20T10:05:00Z',
}

const page = <T,>(content: T[]) => ({ content, page: { size: 15, number: 0, totalElements: content.length, totalPages: 1 } })

const serveBusiness = () => server.use(http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)))

const renderPage = () =>
  renderWithProviders(<AccountsPage />, { path: '/businesses/b1/accounts', route: '/businesses/:businessId/accounts' })

describe('AccountsPage', () => {
  it('offers to open an account, and shows the new card with its IBAN', async () => {
    let accounts: BankAccount[] = []
    serveBusiness()
    server.use(
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json(accounts)),
      http.post('*/api/v1/businesses/b1/accounts', () => {
        accounts = [{ ...account, balance: 0 }]
        return HttpResponse.json(accounts[0], { status: 201 })
      }),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () => HttpResponse.json(page([]))),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Άνοιγμα λογαριασμού' }))

    expect(await screen.findByText('GR36 9990 0010 0000 0000 1000 001')).toBeInTheDocument()
    expect(screen.getByText('Καμία κίνηση ακόμα')).toBeInTheDocument()
  })

  it('shows the balance and the statement with signed amounts', async () => {
    serveBusiness()
    server.use(
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json([account])),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () =>
        HttpResponse.json(page([{ ...income, id: 't2', amount: -200, balanceAfter: 1050.5, counterpartyName: 'Προμηθευτής Α.Ε.' }, income])),
      ),
    )
    renderPage()

    expect(await screen.findByRole('button', { name: /Λογαριασμός όψεως, υπόλοιπο 1\.250,50/ })).toBeInTheDocument()
    const list = within(await screen.findByRole('list', { name: 'Κινήσεις λογαριασμού' }))
    expect(list.getByText('+1.250,50 €')).toBeInTheDocument()
    expect(list.getByText('−200,00 €')).toBeInTheDocument()
    expect(list.getByText('Υπόλοιπο 1.050,50 €')).toBeInTheDocument()
  })

  it('adds a demo deposit and shows the new balance', async () => {
    let balance = 1250.5
    let sent: unknown
    serveBusiness()
    server.use(
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json([{ ...account, balance }])),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () => HttpResponse.json(page([income]))),
      http.post('*/api/v1/businesses/b1/accounts/a1/demo-deposits', async ({ request }) => {
        sent = await request.json()
        balance += 300
        return HttpResponse.json({ ...income, id: 't3', amount: 300, balanceAfter: balance }, { status: 201 })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Κατάθεση demo' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.clear(dialog.getByLabelText('Ποσό (€)'))
    await user.type(dialog.getByLabelText('Ποσό (€)'), '300')
    await user.click(dialog.getByRole('button', { name: 'Κατάθεση' }))

    expect(await screen.findByRole('button', { name: /υπόλοιπο 1\.550,50/ })).toBeInTheDocument()
    expect(sent).toEqual({ amount: 300 })
  })
})
