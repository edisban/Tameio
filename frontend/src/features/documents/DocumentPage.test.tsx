import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeAll, describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import type { DocumentDetails } from './api'
import { DocumentPage } from './DocumentPage'

const business = { id: 'b1', afm: '094014201', name: 'Καφέ Το Στέκι', role: 'OWNER', createdAt: '2026-11-17T10:00:00Z' }

const read: DocumentDetails = {
  id: 'd1',
  filename: 'απόδειξη-0042.pdf',
  mediaType: 'image/png',
  sizeBytes: 1536,
  status: 'READ',
  uploadedAt: '2026-11-17T14:18:00Z',
  readAttempts: 1,
  failure: null,
  failureDetail: null,
  reading: {
    readAt: '2026-11-17T14:18:05Z',
    issuerAfm: { value: '094014201', confidence: 0.98 },
    issueDate: { value: '2026-11-14', confidence: 0.95 },
    netAmount: { value: 10, confidence: 0.9 },
    vatAmount: { value: 2.4, confidence: 0.55 },
    totalAmount: { value: 12.4, confidence: 0.97 },
    category: { code: 'FUEL', confidence: 0.91, modelVersion: 'expense-classifier@1' },
    checks: [
      { name: 'AMOUNTS_ADD_UP', passed: true, detail: '10.00 + 2.40 = 12.40, total 12.40' },
      { name: 'VAT_RATE_KNOWN', passed: false, detail: 'VAT is 24.0% of net' },
    ],
    pages: 1,
    method: 'OCR',
    text: 'ΠΡΑΤΗΡΙΟ ΑΙΓΑΙΟΥ',
  },
  review: null,
}

beforeAll(() => {
  // jsdom has no object URLs; the preview only needs a string to put in src
  URL.createObjectURL = () => 'blob:preview'
  URL.revokeObjectURL = () => {}
})

function serve(document: DocumentDetails) {
  server.use(
    http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
    http.get('*/api/v1/businesses/b1/documents/d1', () => HttpResponse.json(document)),
    http.get('*/api/v1/businesses/b1/documents/d1/content', () => new HttpResponse(new Uint8Array([137, 80, 78, 71]), { headers: { 'Content-Type': 'image/png' } })),
  )
}

function renderPage() {
  return renderWithProviders(<DocumentPage />, {
    path: '/businesses/b1/documents/d1',
    route: '/businesses/:businessId/documents/:documentId',
  })
}

const form = () => screen.findByRole('form', { name: 'Έλεγχος παραστατικού' })

describe('DocumentPage', () => {
  it('shows the file beside what was read, and flags the fields to look at twice', async () => {
    serve(read)
    renderPage()

    // The file arrives after the details, so the first render can take a moment
    expect(await screen.findByRole('img', { name: 'Προεπισκόπηση: απόδειξη-0042.pdf' }, { timeout: 5000 })).toBeInTheDocument()
    const review = within(await form())
    expect(review.getByLabelText('ΑΦΜ εκδότη')).toHaveValue('094014201')
    expect(review.getByLabelText('Σύνολο')).toHaveValue('12,40')
    expect(review.getByText('Ελέγξτε το: βεβαιότητα 55%')).toBeInTheDocument()
    expect(review.getByText('Πρόταση μοντέλου: βεβαιότητα 91%')).toBeInTheDocument()
    expect(review.getByRole('combobox', { name: 'Κατηγορία εξόδου' })).toHaveTextContent('Καύσιμα')

    const checks = within(screen.getByRole('list', { name: 'Έλεγχοι' }))
    expect(checks.getByText('Καθαρό και ΦΠΑ αθροίζουν στο σύνολο')).toBeInTheDocument()
    expect(checks.getByTitle('Απέτυχε')).toBeInTheDocument()
  })

  it('sends the corrected values as numbers and says the corrections help', async () => {
    serve(read)
    let sent: unknown
    server.use(
      http.put('*/api/v1/businesses/b1/documents/d1/review', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({
          ...read,
          status: 'REVIEWED',
          review: { reviewedAt: '2026-11-17T15:00:00Z', issuerAfm: '094014201', issueDate: '2026-11-14', netAmount: 100,
            vatAmount: 24, totalAmount: 124, category: 'TRANSPORT', correctedFields: ['netAmount', 'vatAmount', 'totalAmount', 'category'] },
        })
      }),
    )
    const { user } = renderPage()
    const review = within(await form())

    const typed: [string, string][] = [['Καθαρό', '100'], ['ΦΠΑ', '24,00'], ['Σύνολο', '124']]
    for (const [label, value] of typed) {
      await user.clear(review.getByLabelText(label))
      await user.type(review.getByLabelText(label), value)
    }
    await user.click(review.getByRole('combobox', { name: 'Κατηγορία εξόδου' }))
    await user.click(await screen.findByRole('option', { name: 'Μεταφορές' }))
    await user.click(review.getByRole('button', { name: 'Επιβεβαίωση' }))

    expect(await screen.findByText(/Οι διορθώσεις σας \(4\)/)).toBeInTheDocument()
    expect(sent).toEqual({ issuerAfm: '094014201', issueDate: '2026-11-14', netAmount: 100, vatAmount: 24, totalAmount: 124, category: 'TRANSPORT' })
  })

  it('stops amounts that do not add up before sending them', async () => {
    serve(read)
    const { user } = renderPage()
    const review = within(await form())

    await user.clear(review.getByLabelText('Σύνολο'))
    await user.type(review.getByLabelText('Σύνολο'), '12,50')
    await user.click(review.getByRole('button', { name: 'Επιβεβαίωση' }))

    // No PUT handler is declared, so a request would have failed the test
    expect(await review.findByText('Καθαρό και ΦΠΑ δεν αθροίζουν στο σύνολο')).toBeInTheDocument()
  })

  it('lets a document that could not be read be filled in by hand, or read again', async () => {
    serve({ ...read, status: 'FAILED', failure: 'UNREADABLE', reading: null })
    renderPage()

    expect(await screen.findByText(/Δεν βρέθηκε κείμενο/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ανάγνωση ξανά' })).toBeInTheDocument()
    const review = within(await form())
    expect(review.getByRole('heading', { name: 'Συμπληρώστε τα στοιχεία' })).toBeInTheDocument()
    expect(review.getByLabelText('ΑΦΜ εκδότη')).toHaveValue('')
  })

  it('waits while the document is being read', async () => {
    serve({ ...read, status: 'READING', reading: null })
    renderPage()

    expect(await screen.findByText(/Το έγγραφο διαβάζεται/)).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Έλεγχος παραστατικού' })).not.toBeInTheDocument()
  })
})
