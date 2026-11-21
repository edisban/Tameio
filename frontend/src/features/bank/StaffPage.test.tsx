import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { ChangePasswordPage } from '../../auth/ChangePasswordPage'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import type { StaffMember } from './api'
import { StaffPage } from './StaffPage'

const admin = { id: 'u1', name: 'Άννα', email: 'anna@bank.example', roles: ['ADMIN'], mustChangePassword: false }

const anna: StaffMember = {
  id: 'u1', email: 'anna@bank.example', fullName: 'Άννα Διαχειρίστρια', role: 'ADMIN', approvalLimit: null,
  active: true, mustChangePassword: false, locked: false, createdAt: '2026-11-20T10:00:00Z',
}
const nikos: StaffMember = {
  ...anna, id: 'u2', email: 'nikos@bank.example', fullName: 'Νίκος Παπαδόπουλος', role: 'MANAGER', approvalLimit: 50000,
}

function page(content: StaffMember[]) {
  return { content, page: { size: 100, number: 0, totalElements: content.length, totalPages: 1 } }
}

describe('StaffPage', () => {
  it('lists staff with role, approval limit and state, and keeps an admin from disabling themselves', async () => {
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(admin)),
      http.get('*/api/v1/admin/staff', () => HttpResponse.json(page([anna, { ...nikos, mustChangePassword: true }]))),
    )
    renderWithProviders(<StaffPage />)

    const table = within(await screen.findByRole('table', { name: 'Προσωπικό' }))
    const row = (await table.findByText('Νίκος Παπαδόπουλος')).closest('tr')!
    expect(within(row).getByText('Διευθυντής πιστοδοτήσεων')).toBeInTheDocument()
    expect(within(row).getByText('50.000,00 €')).toBeInTheDocument()
    expect(within(row).getByText('Προσωρινός κωδικός')).toBeInTheDocument()
    expect(await table.findByRole('switch', { name: 'Ενεργός λογαριασμός: Άννα Διαχειρίστρια' })).toBeDisabled()
    expect(table.getByRole('switch', { name: 'Ενεργός λογαριασμός: Νίκος Παπαδόπουλος' })).toBeEnabled()
  })

  it('creates a manager and shows the temporary password once', async () => {
    let sent: unknown
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json(admin)),
      http.get('*/api/v1/admin/staff', () => HttpResponse.json(page([anna]))),
      http.post('*/api/v1/admin/staff', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ user: nikos, temporaryPassword: 'Abcd-EFGH-jkmn-2345' }, { status: 201 })
      }),
    )
    const { user } = renderWithProviders(<StaffPage />)
    await screen.findByRole('table', { name: 'Προσωπικό' })

    await user.click(screen.getByRole('button', { name: 'Νέο μέλος' }))
    const dialog = within(screen.getByRole('dialog'))
    await user.type(dialog.getByLabelText('Ονοματεπώνυμο'), 'Νίκος Παπαδόπουλος')
    await user.type(dialog.getByLabelText('Email'), 'nikos@bank.example')
    await user.click(dialog.getByRole('combobox', { name: 'Ρόλος' }))
    await user.click(await screen.findByRole('option', { name: 'Διευθυντής πιστοδοτήσεων' }))
    await user.type(dialog.getByLabelText('Όριο έγκρισης (€)'), '50.000')
    await user.click(dialog.getByRole('button', { name: 'Δημιουργία' }))

    const shown = within(await screen.findByRole('dialog', { name: 'Προσωρινός κωδικός' }))
    expect(shown.getByText('Abcd-EFGH-jkmn-2345')).toBeInTheDocument()
    expect(sent).toEqual({ fullName: 'Νίκος Παπαδόπουλος', email: 'nikos@bank.example', role: 'MANAGER', approvalLimit: 50000 })
  })
})

describe('ChangePasswordPage', () => {
  it('explains the temporary password and refuses a short or mismatched new one', async () => {
    server.use(http.get('*/api/v1/me', () => HttpResponse.json({ ...admin, mustChangePassword: true })))
    const { user } = renderWithProviders(<ChangePasswordPage />)

    expect(await screen.findByText(/Συνδεθήκατε με προσωρινό κωδικό/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Τωρινός κωδικός'), 'Abcd-EFGH-jkmn-2345')
    await user.type(screen.getByLabelText('Νέος κωδικός'), 'short')
    await user.type(screen.getByLabelText('Νέος κωδικός ξανά'), 'other')
    await user.click(screen.getByRole('button', { name: 'Αλλαγή κωδικού' }))

    // No POST handler is declared, so a request would have failed the test
    expect(await screen.findByText('Τουλάχιστον 12 χαρακτήρες')).toBeInTheDocument()
    expect(screen.getByText('Οι δύο κωδικοί διαφέρουν')).toBeInTheDocument()
  })

  it('sends the change and says when the current password is wrong', async () => {
    server.use(
      http.get('*/api/v1/me', () => HttpResponse.json({ ...admin, mustChangePassword: true })),
      http.post('*/api/v1/me/password', () => HttpResponse.json({ status: 401, detail: 'The current password is wrong.' }, { status: 401 })),
    )
    const { user } = renderWithProviders(<ChangePasswordPage />)

    await user.type(await screen.findByLabelText('Τωρινός κωδικός'), 'wrong one')
    await user.type(screen.getByLabelText('Νέος κωδικός'), 'a long new password')
    await user.type(screen.getByLabelText('Νέος κωδικός ξανά'), 'a long new password')
    await user.click(screen.getByRole('button', { name: 'Αλλαγή κωδικού' }))

    expect(await screen.findByText('Ο τωρινός κωδικός είναι λάθος.')).toBeInTheDocument()
  })
})
