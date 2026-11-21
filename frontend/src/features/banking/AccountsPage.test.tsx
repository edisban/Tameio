import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import { AccountsPage } from './AccountsPage'
import type { BankAccount, Transaction } from './api'

const business = { id: 'b1', afm: '094014201', name: 'Καφέ Το Στέκι', role: 'OWNER', createdAt: '2026-11-17T10:00:00Z' }

const account: BankAccount = {
  id: 'a1', iban: 'GR4499900010000000001000001', name: 'Λογαριασμός όψεως', holderName: 'Καφέ Το Στέκι', currency: 'EUR',
  balance: 1250.5, status: 'ACTIVE', statusReason: null, openedAt: '2026-11-20T10:00:00Z',
}

const income: Transaction = {
  id: 't1', entryId: 'e1', kind: 'INCOMING_PAYMENT', description: 'Incoming payment', reference: 'Demo', amount: 1250.5,
  balanceAfter: 1250.5, counterpartyName: 'Πελάτης Demo Α.Ε.', counterpartyIban: 'GR96…', bookedAt: '2026-11-20T10:05:00Z',
}

const page = <T,>(content: T[]) => ({ content, page: { size: 15, number: 0, totalElements: content.length, totalPages: 1 } })

const renderPage = () =>
  renderWithProviders(<AccountsPage />, { path: '/businesses/b1/accounts', route: '/businesses/:businessId/accounts' })

describe('AccountsPage', () => {
  it('offers to open an account, and shows the new card with its IBAN', async () => {
    let accounts: BankAccount[] = []
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json(accounts)),
      http.post('*/api/v1/businesses/b1/accounts', () => {
        accounts = [{ ...account, balance: 0 }]
        return HttpResponse.json(accounts[0], { status: 201 })
      }),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () => HttpResponse.json(page([]))),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Άνοιγμα λογαριασμού' }))

    expect(await screen.findByText('GR44 9990 0010 0000 0000 1000 001')).toBeInTheDocument()
    expect(screen.getByText('Καμία κίνηση ακόμα')).toBeInTheDocument()
  })

  it('shows the balance and the statement with signed amounts', async () => {
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
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

  it('pays after a review step, and a retry after a failure reuses the same Idempotency-Key', async () => {
    const keys: (string | null)[] = []
    let sent: unknown
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json([account])),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () => HttpResponse.json(page([income]))),
      http.post('*/api/v1/businesses/b1/accounts/a1/transfers', async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        sent = await request.json()
        // The first answer is lost on the way back
        if (keys.length === 1) return HttpResponse.json({ status: 503 }, { status: 503 })
        return HttpResponse.json({
          id: 'e9', status: 'COMPLETED', amount: 120.5, debtorIban: account.iban, creditorIban: 'GR1601101250000000012300695',
          creditorName: 'Προμηθευτής Α.Ε.', reference: 'ΤΙΜ 42', bookedAt: '2026-11-20T11:00:00Z', balanceAfter: 1130,
        }, { status: 201 })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Νέα μεταφορά' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText('IBAN δικαιούχου'), 'gr1601101250000000012300695')
    expect(dialog.getByLabelText('IBAN δικαιούχου')).toHaveValue('GR16 0110 1250 0000 0001 2300 695')
    await user.type(dialog.getByLabelText('Όνομα δικαιούχου'), 'Προμηθευτής Α.Ε.')
    await user.type(dialog.getByLabelText('Ποσό (€)'), '120,50')
    await user.type(dialog.getByLabelText('Αιτιολογία'), 'ΤΙΜ 42')
    await user.click(dialog.getByRole('button', { name: 'Συνέχεια' }))

    const review = within(await screen.findByRole('dialog'))
    expect(review.getByText('Επιβεβαίωση πληρωμής')).toBeInTheDocument()
    await user.click(review.getByRole('button', { name: /Πληρωμή 120,50/ }))
    expect(await review.findByText(/δεν θα χρεωθείτε δύο φορές/)).toBeInTheDocument()
    await user.click(review.getByRole('button', { name: /Πληρωμή 120,50/ }))

    expect(await screen.findByText('Η πληρωμή ολοκληρώθηκε')).toBeInTheDocument()
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBeTruthy()
    expect(keys[1]).toBe(keys[0])
    expect(sent).toEqual({ creditorIban: 'GR1601101250000000012300695', creditorName: 'Προμηθευτής Α.Ε.', amount: 120.5, reference: 'ΤΙΜ 42' })
  })

  it('catches a mistyped IBAN before anything is sent', async () => {
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json([account])),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () => HttpResponse.json(page([]))),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Νέα μεταφορά' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText('IBAN δικαιούχου'), 'GR1601101250000000012300696')
    await user.type(dialog.getByLabelText('Όνομα δικαιούχου'), 'Κάποιος')
    await user.type(dialog.getByLabelText('Ποσό (€)'), '10')
    await user.click(dialog.getByRole('button', { name: 'Συνέχεια' }))

    expect(await dialog.findByText('Ελέγξτε το IBAN: δεν είναι έγκυρο')).toBeInTheDocument()
  })
})
