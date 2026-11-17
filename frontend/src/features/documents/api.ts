import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiBlob, type Page } from '../../api/http'

export type DocumentStatus = 'UPLOADED'

export type DocumentItem = {
  id: string
  filename: string
  mediaType: string
  sizeBytes: number
  status: DocumentStatus
  uploadedAt: string
}

const base = (businessId: string) => `/api/v1/businesses/${businessId}/documents`

const keys = {
  all: (businessId: string) => ['documents', businessId] as const,
  page: (businessId: string, page: number, size: number) => ['documents', businessId, { page, size }] as const,
}

export function useDocuments(businessId: string, page: number, size: number) {
  return useQuery({
    queryKey: keys.page(businessId, page, size),
    queryFn: () => api<Page<DocumentItem>>(`${base(businessId)}?page=${page}&size=${size}`),
    // Keep showing the current page while the next one loads, instead of flashing a skeleton
    placeholderData: keepPreviousData,
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
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keys.all(businessId) }),
  })
}

/** Opens the document in a new tab. The file is fetched with the user's token, then shown from memory. */
export async function openDocument(businessId: string, document: DocumentItem) {
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
