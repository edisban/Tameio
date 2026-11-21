import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { server } from '../test/server'
import { api, ApiError } from './http'

const CSRF_COOKIE = 'tameio_xsrf'

/** As the browser does with the Set-Cookie of the server's answer. */
const browserStoresCsrfCookie = (token: string) => {
  document.cookie = `${CSRF_COOKIE}=${token}; path=/`
}

afterEach(() => {
  document.cookie = `${CSRF_COOKIE}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
})

describe('api', () => {
  it('sends a change again with the CSRF token when it went out before the page had one', async () => {
    const tokens: (string | null)[] = []
    server.use(
      http.post('*/api/v1/auth/sign-in', ({ request }) => {
        const token = request.headers.get('X-XSRF-TOKEN')
        tokens.push(token)
        if (!token) {
          browserStoresCsrfCookie('fresh-token')
          return new HttpResponse(null, { status: 401 })
        }
        return HttpResponse.json({ id: 'u1' })
      }),
    )

    const signedIn = await api<{ id: string }>('/api/v1/auth/sign-in', { method: 'POST', body: '{}' })

    expect(signedIn).toEqual({ id: 'u1' })
    expect(tokens).toEqual([null, 'fresh-token'])
  })

  it('does not send a change again when it already carried the token', async () => {
    browserStoresCsrfCookie('known-token')
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/sign-in', () => {
        calls++
        return HttpResponse.json({ status: 401, title: 'Unauthorized' }, { status: 401 })
      }),
    )

    await expect(api('/api/v1/auth/sign-in', { method: 'POST', body: '{}' })).rejects.toBeInstanceOf(ApiError)
    expect(calls).toBe(1)
  })
})
