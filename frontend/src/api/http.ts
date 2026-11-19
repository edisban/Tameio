import { ApiError, type Problem } from './errors'

export { ApiError, type Problem }

/** One page of a list, as Spring Data returns it. */
export type Page<T> = {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

// Must match SessionCookies on the server
const CSRF_COOKIE = 'tameio_xsrf'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find(cookie => cookie.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/**
 * Calls the API and turns error responses into an ApiError. The session travels in an HttpOnly
 * cookie the browser adds by itself; for changes, the CSRF token from its own cookie goes in a header,
 * which proves the request comes from this page and not from another site.
 */
async function request(path: string, init: RequestInit, accept: string): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', accept)
  // JSON bodies are sent as strings; FormData sets its own multipart Content-Type with the boundary
  if (typeof init.body === 'string') headers.set('Content-Type', 'application/json')
  const method = (init.method ?? 'GET').toUpperCase()
  const csrf = readCookie(CSRF_COOKIE)
  if (!SAFE_METHODS.has(method) && csrf) headers.set(CSRF_HEADER, decodeURIComponent(csrf))

  // Resolved against the page's own origin, which is what the browser does anyway and what Node's fetch needs in tests
  const response = await fetch(new URL(path, window.location.origin), { ...init, headers, credentials: 'same-origin' })
  if (!response.ok) {
    const isProblem = response.headers.get('Content-Type')?.includes('json')
    const problem: Problem = isProblem ? await response.json() : { status: response.status }
    throw new ApiError({ ...problem, status: response.status })
  }
  return response
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await request(path, init, 'application/json')
  return response.status === 204 ? (undefined as T) : response.json()
}

/** Downloads a file the API protects; the session cookie goes with it. */
export async function apiBlob(path: string): Promise<Blob> {
  const response = await request(path, {}, '*/*')
  return response.blob()
}
