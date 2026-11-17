import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import { RegisterBusinessDialog } from './RegisterBusinessDialog'

function renderDialog() {
  const onClose = vi.fn()
  const view = renderWithProviders(<RegisterBusinessDialog open onClose={onClose} />)
  return { onClose, ...view }
}

describe('RegisterBusinessDialog', () => {
  it('checks the ΑΦΜ before anything is sent', async () => {
    const { user } = renderDialog()

    await user.type(screen.getByLabelText('Επωνυμία'), 'Καφέ Το Στέκι')
    await user.type(screen.getByLabelText('ΑΦΜ'), '123456789')
    await user.click(screen.getByRole('button', { name: 'Καταχώριση' }))

    // No request handler is declared: had the form called the API, MSW would have failed the test
    expect(await screen.findByText('Μη έγκυρο ΑΦΜ')).toBeInTheDocument()
  })

  it('registers the business and closes', async () => {
    let sent: unknown
    server.use(
      http.post('*/api/v1/businesses', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(
          { id: 'b1', afm: '094014201', name: 'Καφέ Το Στέκι', role: 'OWNER', createdAt: '2026-11-17T10:00:00Z' },
          { status: 201 },
        )
      }),
    )
    const { user, onClose } = renderDialog()

    await user.type(screen.getByLabelText('Επωνυμία'), 'Καφέ Το Στέκι')
    await user.type(screen.getByLabelText('ΑΦΜ'), '094014201')
    await user.click(screen.getByRole('button', { name: 'Καταχώριση' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(sent).toEqual({ name: 'Καφέ Το Στέκι', afm: '094014201' })
  })

  it('shows a taken ΑΦΜ under the ΑΦΜ field, in Greek', async () => {
    server.use(
      http.post('*/api/v1/businesses', () =>
        HttpResponse.json({ status: 409, title: 'Conflict', detail: 'A business with this ΑΦΜ is already registered.' }, { status: 409 }),
      ),
    )
    const { user, onClose } = renderDialog()

    await user.type(screen.getByLabelText('Επωνυμία'), 'Δεύτερη')
    await user.type(screen.getByLabelText('ΑΦΜ'), '094014201')
    await user.click(screen.getByRole('button', { name: 'Καταχώριση' }))

    expect(await screen.findByText('Υπάρχει ήδη επιχείρηση με αυτό το ΑΦΜ')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('says so when the server cannot be reached', async () => {
    server.use(http.post('*/api/v1/businesses', () => HttpResponse.error()))
    const { user } = renderDialog()

    await user.type(screen.getByLabelText('Επωνυμία'), 'Καφέ')
    await user.type(screen.getByLabelText('ΑΦΜ'), '094014201')
    await user.click(screen.getByRole('button', { name: 'Καταχώριση' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Η καταχώριση δεν ολοκληρώθηκε. Δοκιμάστε ξανά.')
  })
})
