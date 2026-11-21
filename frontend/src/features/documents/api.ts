import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiBlob, type Page } from '../../api/http'

export type DocumentStatus = 'UPLOADED' | 'READING' | 'READ' | 'FAILED' | 'REVIEWED'
export type ReadFailure = 'UNREADABLE' | 'REJECTED' | 'GAVE_UP'

export type DocumentItem = {
  id: string
  filename: string
  mediaType: string
  sizeBytes: number
  status: DocumentStatus
  uploadedAt: string
  /** What document AI read, once it has */
  summary: { issueDate: string | null; issuerAfm: string | null; totalAmount: number | null; category: string } | null
  failure: ReadFailure | null
}

export type Found<T> = { value: T; confidence: number }

export type DocumentDetails = {
  id: string
  filename: string
  mediaType: string
  sizeBytes: number
  status: DocumentStatus
  uploadedAt: string
  readAttempts: number
  failure: ReadFailure | null
  failureDetail: string | null
  reading: {
    readAt: string
    issuerAfm: Found<string> | null
    issueDate: Found<string> | null
    netAmount: Found<number> | null
    vatAmount: Found<number> | null
    totalAmount: Found<number> | null
    category: { code: string; confidence: number; modelVersion: string }
    checks: { name: string; passed: boolean; detail: string }[]
    pages: number
    method: string
    text: string
  } | null
  /** What a person confirmed */
  review: {
    reviewedAt: string
    issuerAfm: string
    issueDate: string
    netAmount: number
    vatAmount: number
    totalAmount: number
    category: string
    correctedFields: string[]
  } | null
}

export type ReviewInput = {
  issuerAfm: string
  issueDate: string
  netAmount: number
  vatAmount: number
  totalAmount: number
  category: string
}

/** Still with document AI: the list keeps asking until it has an answer */
export const isPending = (status: DocumentStatus) => status === 'UPLOADED' || status === 'READING'

const POLL_MS = 2_000

const base = (businessId: string) => `/api/v1/businesses/${businessId}/documents`

const keys = {
  all: (businessId: string) => ['documents', businessId] as const,
  page: (businessId: string, page: number, size: number) => ['documents', businessId, { page, size }] as const,
  one: (businessId: string, documentId: string) => ['documents', businessId, 'one', documentId] as const,
}

export function useDocuments(businessId: string, page: number, size: number) {
  return useQuery({
    queryKey: keys.page(businessId, page, size),
    queryFn: () => api<Page<DocumentItem>>(`${base(businessId)}?page=${page}&size=${size}`),
    // Keep showing the current page while the next one loads, instead of flashing a skeleton
    placeholderData: keepPreviousData,
    // Reading takes a few seconds; ask again until every document on the page has an answer
    refetchInterval: query => (query.state.data?.content.some(d => isPending(d.status)) ? POLL_MS : false),
  })
}

export function useDocument(businessId: string, documentId: string) {
  return useQuery({
    queryKey: keys.one(businessId, documentId),
    queryFn: () => api<DocumentDetails>(`${base(businessId)}/${documentId}`),
    refetchInterval: query => (query.state.data && isPending(query.state.data.status) ? POLL_MS : false),
  })
}

export function useReviewDocument(businessId: string, documentId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ReviewInput) =>
      api<DocumentDetails>(`${base(businessId)}/${documentId}/review`, { method: 'PUT', body: JSON.stringify(input) }),
    onSuccess: saved => {
      queryClient.setQueryData(keys.one(businessId, documentId), saved)
      return queryClient.invalidateQueries({ queryKey: keys.all(businessId) })
    },
  })
}

export function useReadAgain(businessId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (documentId: string) => api<DocumentItem>(`${base(businessId)}/${documentId}/read`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all(businessId) }),
  })
}

export function useUploadDocument(businessId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const body = new FormData()
      body.append('file', file)
      return api<DocumentItem>(base(businessId), { method: 'POST', body })
    },
    onSuccess: created => {
      // Shown at once on the first page from the answer itself, then confirmed by a fresh read
      queryClient.setQueriesData<Page<DocumentItem>>({ queryKey: keys.all(businessId) }, old =>
        old && Array.isArray(old.content) && old.page.number === 0
          ? {
              content: [created, ...old.content.filter(d => d.id !== created.id)].slice(0, old.page.size),
              page: { ...old.page, totalElements: old.page.totalElements + 1 },
            }
          : old,
      )
      return queryClient.invalidateQueries({ queryKey: keys.all(businessId) })
    },
  })
}

/** Opens the document in a new tab. The file is fetched with the user's token, then shown from memory. */
export async function openDocument(businessId: string, document: Pick<DocumentItem, 'id'>) {
  // Open the tab right away, inside the click, so the browser does not treat it as a pop-up
  const tab = window.open('', '_blank')
  try {
    const blob = await apiBlob(`${base(businessId)}/${document.id}/content`)
    const url = URL.createObjectURL(blob)
    if (tab) tab.location.href = url
    // The tab has loaded the file by then; free the memory afterwards
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (error) {
    tab?.close()
    throw error
  }
}
