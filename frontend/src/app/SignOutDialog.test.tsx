import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { Layout } from './Layout'

const jack = { id: 'u1', name: 'Jack Harbour', email: 'jack@tameio.local', roles: ['BUSINESS'], mustChangePassword: false }

function signedInAsJack() {
  server.use(http.get('*/api/v1/me', () => HttpResponse.json(jack)))
}

describe('Signing out', () => {
  it('asks first, and does nothing when the answer is no', async () => {
    signedInAsJack()
    const { user } = renderWithProviders(<Layout />)

    await user.click(await screen.findByRole('button', { name: 'Αποσύνδεση' }))

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Είστε σίγουροι ότι θέλετε να αποσυνδεθείτε;')
    // No sign-out handler is declared: a request would have failed the test
    await user.click(screen.getByRole('button', { name: 'Άκυρο' }))
    await expect.poll(() => screen.queryByRole('dialog')).toBeNull()
  })

  it('signs out once confirmed and says so', async () => {
    signedInAsJack()
    let signedOut = false
    server.use(
      http.post('*/api/v1/auth/sign-out', () => {
        signedOut = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderWithProviders(<Layout />)

    await user.click(await screen.findByRole('button', { name: 'Αποσύνδεση' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Αποσύνδεση' }))

    expect(await screen.findByText('Αποσυνδεθήκατε επιτυχώς.')).toBeInTheDocument()
    expect(signedOut).toBe(true)
  })

  it('warns that signing out everywhere ends the other devices too', async () => {
    signedInAsJack()
    const { user } = renderWithProviders(<Layout />)

    await user.click(await screen.findByRole('button', { name: 'Αποσύνδεση από όλες τις συσκευές' }))

    expect(await screen.findByRole('dialog')).toHaveTextContent('σε κάθε άλλη συσκευή')
  })
})
