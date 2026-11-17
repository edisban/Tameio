import { accessToken } from '../auth/oidc'
import { ApiError, type Problem } from './errors'

export { ApiError, type Problem }

/** One page of a list, as Spring Data returns it. */
export type Page<T> = {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

/** Calls the API with the signed-in user's token and turns error responses into an ApiError. */
async function request(path: string, init: RequestInit, accept: string): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', accept)
  // JSON bodies are sent as strings; FormData sets its own multipart Content-Type with the boundary
  if (typeof init.body === 'string') headers.set('Content-Type', 'application/json')
  const token = accessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  // Resolved against the page's own origin, which is what the browser does anyway and what Node's fetch needs in tests
  const response = await fetch(new URL(path, window.location.origin), { ...init, headers })
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

/** Downloads a file the API protects, which a plain link cannot do because it carries no token. */
export async function apiBlob(path: string): Promise<Blob> {
  const response = await request(path, {}, '*/*')
  return response.blob()
}
