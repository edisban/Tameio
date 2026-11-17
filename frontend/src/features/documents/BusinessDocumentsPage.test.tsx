import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { Page } from '../../api/http'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import type { DocumentItem } from './api'
import { BusinessDocumentsPage } from './BusinessDocumentsPage'

const business = { id: 'b1', afm: '094014201', name: 'Καφέ Το Στέκι', role: 'OWNER', createdAt: '2026-11-17T10:00:00Z' }

const receipt: DocumentItem = {
  id: 'd1',
  filename: 'απόδειξη-0042.pdf',
  mediaType: 'application/pdf',
  sizeBytes: 1536,
  status: 'UPLOADED',
  uploadedAt: '2026-11-17T14:18:00Z',
}

function page(content: DocumentItem[]): Page<DocumentItem> {
  return { content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } }
}

function renderPage() {
  return renderWithProviders(<BusinessDocumentsPage />, { path: '/businesses/b1', route: '/businesses/:businessId' })
}

describe('BusinessDocumentsPage', () => {
  it('shows the business and an empty state before any upload', async () => {
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
      http.get('*/api/v1/businesses/b1/documents', () => HttpResponse.json(page([]))),
    )
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Καφέ Το Στέκι' })).toBeInTheDocument()
    expect(screen.getByText('ΑΦΜ 094014201')).toBeInTheDocument()
    expect(await screen.findByText('Κανένα παραστατικό ακόμα')).toBeInTheDocument()
  })

  it('lists documents with their size and date in Greek format', async () => {
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
      http.get('*/api/v1/businesses/b1/documents', () => HttpResponse.json(page([receipt]))),
    )
    renderPage()

    const table = await screen.findByRole('table', { name: 'Παραστατικά' })
    const row = within(table).getByRole('button', { name: 'απόδειξη-0042.pdf' }).closest('tr')!
    expect(within(row).getByText('1,5 KB')).toBeInTheDocument()
    expect(within(row).getByText('17 Νοε 2026, 4:18 μ.μ.')).toBeInTheDocument()
    expect(within(row).getByText('Σε αναμονή ανάγνωσης')).toBeInTheDocument()
  })

  it('uploads a file and adds it to the list', async () => {
    let documents: DocumentItem[] = []
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
      http.get('*/api/v1/businesses/b1/documents', () => HttpResponse.json(page(documents))),
      http.post('*/api/v1/businesses/b1/documents', async ({ request }) => {
        // jsdom's File loses its name when Node builds the multipart body, so the fake answers with the
        // name the test chose and checks what it can: that the file's bytes arrived
        const file = (await request.formData()).get('file') as Blob
        documents = [{ ...receipt, filename: 'τιμολόγιο.pdf', sizeBytes: file.size }]
        return HttpResponse.json(documents[0], { status: 201 })
      }),
    )
    const { user } = renderPage()
    await screen.findByText('Κανένα παραστατικό ακόμα')

    await user.upload(screen.getByLabelText('Επιλογή αρχείων', { selector: 'input' }), new File(['%PDF-1.7'], 'τιμολόγιο.pdf', { type: 'application/pdf' }))

    expect(await screen.findByText('Ανέβηκε')).toBeInTheDocument()
    const row = (await screen.findByRole('button', { name: 'τιμολόγιο.pdf' })).closest('tr')!
    expect(within(row).getByText('8 B')).toBeInTheDocument()
  })

  it('stops a file that is too large without sending it', async () => {
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
      http.get('*/api/v1/businesses/b1/documents', () => HttpResponse.json(page([]))),
    )
    const { user } = renderPage()
    await screen.findByText('Κανένα παραστατικό ακόμα')
    const big = new File(['x'], 'σάρωση.pdf', { type: 'application/pdf' })
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 })

    await user.upload(screen.getByLabelText('Επιλογή αρχείων', { selector: 'input' }), big)

    // No POST handler is declared, so a request would have failed the test
    expect(await screen.findByText('Το αρχείο ξεπερνά τα 10 MB.')).toBeInTheDocument()
  })

  it('explains in Greek when the server refuses the content', async () => {
    server.use(
      http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
      http.get('*/api/v1/businesses/b1/documents', () => HttpResponse.json(page([]))),
      http.post('*/api/v1/businesses/b1/documents', () =>
        HttpResponse.json({ status: 415, detail: 'Upload a PDF, JPEG or PNG file.' }, { status: 415 }),
      ),
    )
    const { user } = renderPage()
    await screen.findByText('Κανένα παραστατικό ακόμα')

    await user.upload(screen.getByLabelText('Επιλογή αρχείων', { selector: 'input' }), new File(['text'], 'fake.pdf', { type: 'application/pdf' }))

    expect(await screen.findByText('Το περιεχόμενο δεν είναι PDF, JPEG ή PNG.')).toBeInTheDocument()
  })
})
