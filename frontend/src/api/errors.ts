/** An RFC 9457 problem detail, as the API returns for every error. */
export type Problem = {
  status: number
  title?: string
  detail?: string
  /** Names the business rule behind a refusal, so the page can explain it in Greek. */
  code?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly problem: Problem

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`)
    this.problem = problem
  }
}
