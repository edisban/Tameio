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

/** What every page load asks for besides the accounts: payments, the demo payees, and the payee check. */
function serveBasics(check: { result: string; actualName: string | null } = { result: 'MATCH', actualName: null }) {
  server.use(
    http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
    http.get('*/api/v1/businesses/b1/accounts/a1/payments', () => HttpResponse.json(page([]))),
    http.get('*/api/v1/clearing/directory', () => HttpResponse.json([])),
    http.post('*/api/v1/payee-verification', () => HttpResponse.json(check)),
  )
}

const renderPage = () =>
  renderWithProviders(<AccountsPage />, { path: '/businesses/b1/accounts', route: '/businesses/:businessId/accounts' })

describe('AccountsPage', () => {
  it('offers to open an account, and shows the new card with its IBAN', async () => {
    let accounts: BankAccount[] = []
    serveBasics()
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
    serveBasics()
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

  it('pays after a review step, and a retry after a failure reuses the same Idempotency-Key', async () => {
    const keys: (string | null)[] = []
    let sent: unknown
    serveBasics()
    server.use(
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json([account])),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () => HttpResponse.json(page([income]))),
      http.post('*/api/v1/businesses/b1/accounts/a1/transfers', async ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        sent = await request.json()
        // The first answer is lost on the way back
        if (keys.length === 1) return HttpResponse.json({ status: 503 }, { status: 503 })
        return HttpResponse.json({
          id: 'p9', direction: 'OUTGOING', scheme: 'SEPA', status: 'PENDING', amount: 120.5, accountIban: account.iban,
          counterpartyIban: 'GR1601101250000000012300695', counterpartyName: 'Προμηθευτής Α.Ε.', reference: 'ΤΙΜ 42',
          endToEndId: 'KYMA123', payeeCheck: 'MATCH', reasonCode: null, reason: null, createdAt: '2026-11-20T11:00:00Z', balanceAfter: 1130,
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
    expect(review.getByText('Το όνομα ταιριάζει με τον κάτοχο του λογαριασμού.')).toBeInTheDocument()
    expect(review.getByText(/SEPA, προς άλλη τράπεζα/)).toBeInTheDocument()
    await user.click(review.getByRole('button', { name: /Πληρωμή 120,50/ }))
    expect(await review.findByText(/δεν θα χρεωθείτε δύο φορές/)).toBeInTheDocument()
    await user.click(review.getByRole('button', { name: /Πληρωμή 120,50/ }))

    expect(await screen.findByText('Η πληρωμή στάλθηκε')).toBeInTheDocument()
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBeTruthy()
    expect(keys[1]).toBe(keys[0])
    expect(sent).toEqual({ creditorIban: 'GR1601101250000000012300695', creditorName: 'Προμηθευτής Α.Ε.', amount: 120.5, reference: 'ΤΙΜ 42' })
  })

  it('catches a mistyped IBAN before anything is sent', async () => {
    serveBasics()
    server.use(
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

  it('asks the payer to confirm when the name does not match the account, and sends that confirmation', async () => {
    serveBasics({ result: 'NO_MATCH', actualName: null })
    let sent: { confirmedMismatch?: boolean } | undefined
    server.use(
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json([account])),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () => HttpResponse.json(page([]))),
      http.post('*/api/v1/businesses/b1/accounts/a1/transfers', async ({ request }) => {
        sent = (await request.json()) as { confirmedMismatch?: boolean }
        return HttpResponse.json({ id: 'p1', direction: 'OUTGOING', scheme: 'INTERNAL', status: 'COMPLETED', amount: 10,
          accountIban: account.iban, counterpartyIban: 'GR3699900010000000001000001', counterpartyName: 'Λάθος Όνομα', reference: null,
          endToEndId: 'KYMA1', payeeCheck: 'NO_MATCH', reasonCode: null, reason: null, createdAt: '2026-11-20T11:00:00Z', balanceAfter: 1240.5 },
          { status: 201 })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Νέα μεταφορά' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText('IBAN δικαιούχου'), 'GR3699900010000000001000001')
    await user.type(dialog.getByLabelText('Όνομα δικαιούχου'), 'Λάθος Όνομα')
    await user.type(dialog.getByLabelText('Ποσό (€)'), '10')
    await user.click(dialog.getByRole('button', { name: 'Συνέχεια' }))

    const review = within(await screen.findByRole('dialog'))
    expect(await review.findByText(/δεν ταιριάζει με τον κάτοχο/)).toBeInTheDocument()
    expect(review.getByText(/Μεταφορά στην Kyma Bank: άμεσα/)).toBeInTheDocument()
    const payButton = review.getByRole('button', { name: /Πληρωμή 10,00/ })
    expect(payButton).toBeDisabled()

    await user.click(review.getByRole('checkbox', { name: 'Το κατανοώ και θέλω να πληρώσω' }))
    await user.click(payButton)

    expect(await screen.findByText('Η πληρωμή ολοκληρώθηκε')).toBeInTheDocument()
    expect(sent?.confirmedMismatch).toBe(true)
  })

  it('offers the name on the account after a close match', async () => {
    serveBasics({ result: 'CLOSE_MATCH', actualName: 'ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ Ο.Ε.' })
    server.use(
      http.get('*/api/v1/businesses/b1/accounts', () => HttpResponse.json([account])),
      http.get('*/api/v1/businesses/b1/accounts/a1/transactions', () => HttpResponse.json(page([]))),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Νέα μεταφορά' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText('IBAN δικαιούχου'), 'GR1601101250000000012300695')
    await user.type(dialog.getByLabelText('Όνομα δικαιούχου'), 'Χαρτικα Αιγαιυο')
    await user.type(dialog.getByLabelText('Ποσό (€)'), '10')
    await user.click(dialog.getByRole('button', { name: 'Συνέχεια' }))

    const review = within(await screen.findByRole('dialog'))
    expect(await review.findByText(/ο λογαριασμός ανήκει σε «ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ Ο.Ε.»/)).toBeInTheDocument()
    await user.click(review.getByRole('button', { name: 'Χρήση' }))

    expect(review.getByText('ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ Ο.Ε.')).toBeInTheDocument()
    expect(review.getByText('Το όνομα ταιριάζει με τον κάτοχο του λογαριασμού.')).toBeInTheDocument()
  })
})
