import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { SignInPage } from './SignInPage'

const emma = { id: 'u1', name: 'Emma Wilson', email: 'emma@example.com', roles: ['BUSINESS'] }

function signedOut() {
  server.use(http.get('*/api/v1/me', () => HttpResponse.json({ status: 401 }, { status: 401 })))
}

async function fillAndSubmit(user: ReturnType<typeof renderWithProviders>['user'], email: string, password: string) {
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Κωδικός'), password)
  await user.click(screen.getByRole('button', { name: 'Σύνδεση' }))
}

describe('SignInPage', () => {
  it('signs in and sends the user to their businesses', async () => {
    signedOut()
    let sent: unknown
    server.use(
      http.post('*/api/v1/auth/sign-in', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(emma)
      }),
      http.get('*/api/v1/businesses', () => HttpResponse.json([])),
    )
    const { user } = renderWithProviders(<SignInPage />, { path: '/sign-in', route: '/sign-in' })

    await fillAndSubmit(user, 'emma@example.com', 'tameio-demo')

    expect(sent).toEqual({ email: 'emma@example.com', password: 'tameio-demo' })
  })

  it('says the email or password is wrong, without saying which', async () => {
    signedOut()
    server.use(
      http.post('*/api/v1/auth/sign-in', () => HttpResponse.json({ status: 401, detail: 'Wrong email or password.' }, { status: 401 })),
    )
    const { user } = renderWithProviders(<SignInPage />, { path: '/sign-in', route: '/sign-in' })

    await fillAndSubmit(user, 'emma@example.com', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent('Λάθος email ή κωδικός.')
  })

  it('explains a locked account', async () => {
    signedOut()
    server.use(http.post('*/api/v1/auth/sign-in', () => HttpResponse.json({ status: 429 }, { status: 429 })))
    const { user } = renderWithProviders(<SignInPage />, { path: '/sign-in', route: '/sign-in' })

    await fillAndSubmit(user, 'emma@example.com', 'wrong')

    expect(await screen.findByRole('alert')).toHaveTextContent('Ο λογαριασμός κλείδωσε για 15 λεπτά.')
  })

  it('asks for both fields before sending anything', async () => {
    signedOut()
    const { user } = renderWithProviders(<SignInPage />, { path: '/sign-in', route: '/sign-in' })

    await user.click(await screen.findByRole('button', { name: 'Σύνδεση' }))

    // No sign-in handler is declared: a request would have failed the test
    expect(await screen.findByText('Γράψτε το email σας')).toBeInTheDocument()
    expect(screen.getByText('Γράψτε τον κωδικό σας')).toBeInTheDocument()
  })

  it('shows the password when asked, to check what was typed', async () => {
    signedOut()
    const { user } = renderWithProviders(<SignInPage />, { path: '/sign-in', route: '/sign-in' })
    const password = await screen.findByLabelText('Κωδικός')
    await user.type(password, 'αναλψστ123')

    await user.click(screen.getByRole('button', { name: 'Εμφάνιση κωδικού' }))

    expect(password).toHaveAttribute('type', 'text')
    expect(password).toHaveValue('αναλψστ123')
    await user.click(screen.getByRole('button', { name: 'Απόκρυψη κωδικού' }))
    expect(password).toHaveAttribute('type', 'password')
  })

  it('sends the CSRF token from its cookie with the sign-in', async () => {
    signedOut()
    document.cookie = 'tameio_xsrf=token-123; path=/'
    let header: string | null = null
    server.use(
      http.post('*/api/v1/auth/sign-in', ({ request }) => {
        header = request.headers.get('X-XSRF-TOKEN')
        return HttpResponse.json(emma)
      }),
    )
    const { user } = renderWithProviders(<SignInPage />, { path: '/sign-in', route: '/sign-in' })

    await fillAndSubmit(user, 'emma@example.com', 'tameio-demo')

    await expect.poll(() => header).toBe('token-123')
    document.cookie = 'tameio_xsrf=; max-age=0; path=/'
  })
})
