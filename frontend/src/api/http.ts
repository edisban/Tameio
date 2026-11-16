import { accessToken } from '../auth/oidc'

/** An RFC 9457 problem detail, as the API returns for every error. */
export type Problem = {
  status: number
  title?: string
  detail?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly problem: Problem

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`)
    this.problem = problem
  }
}

/** Calls the API with the signed-in user's token and turns error responses into an ApiError. */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  const token = accessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    const isProblem = response.headers.get('Content-Type')?.includes('json')
    const problem: Problem = isProblem ? await response.json() : { status: response.status }
    throw new ApiError({ ...problem, status: response.status })
  }
  return response.status === 204 ? (undefined as T) : response.json()
}
