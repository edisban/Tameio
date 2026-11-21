import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import type { Expense, YearSummary } from './api'
import { AccountingPage } from './AccountingPage'
import { niceMax } from './charts'

const business = { id: 'b1', afm: '094014201', name: 'Καφέ Το Στέκι', role: 'OWNER', createdAt: '2026-11-17T10:00:00Z' }
const zero = { net: 0, vat: 0, total: 0, count: 0 }
const year = new Date().getFullYear()

const summary: YearSummary = {
  year,
  totals: { net: 160, vat: 34.1, total: 194.1, count: 3 },
  months: Array.from({ length: 12 }, (_, i) => ({
    number: i + 1,
    totals: i === 1 ? { net: 110, vat: 26.4, total: 136.4, count: 2 } : i === 10 ? { net: 50, vat: 7.7, total: 57.7, count: 1 } : zero,
  })),
  quarters: [
    { number: 1, totals: { net: 110, vat: 26.4, total: 136.4, count: 2 } },
    { number: 2, totals: zero },
    { number: 3, totals: zero },
    { number: 4, totals: { net: 50, vat: 7.7, total: 57.7, count: 1 } },
  ],
  categories: [
    { category: 'RENT', totals: { net: 100, vat: 24, total: 124, count: 1 } },
    { category: 'GROCERIES', totals: { net: 50, vat: 7.7, total: 57.7, count: 1 } },
  ],
}

const rent: Expense = {
  id: 'e1', documentId: 'd1', supplierAfm: '094014201', issueDate: `${year}-02-01`,
  netAmount: 100, vatAmount: 24, totalAmount: 124, category: 'RENT',
}

function serve() {
  server.use(
    http.get('*/api/v1/businesses/b1', () => HttpResponse.json(business)),
    http.get('*/api/v1/businesses/b1/accounting/summary', () => HttpResponse.json(summary)),
    http.get('*/api/v1/businesses/b1/expenses', ({ request }) => {
      const category = new URL(request.url).searchParams.get('category')
      const content = category && category !== 'RENT' ? [] : [rent]
      return HttpResponse.json({ content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } })
    }),
  )
}

const renderPage = () => renderWithProviders(<AccountingPage />, { path: '/businesses/b1/accounting', route: '/businesses/:businessId/accounting' })

describe('AccountingPage', () => {
  it('shows the year in figures, by month, by category and the VAT of each quarter', async () => {
    serve()
    renderPage()

    expect(await screen.findByText('194,10 €')).toBeInTheDocument()
    expect(screen.getByText('34,10 €')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: `Έξοδα ανά μήνα το ${year}` })).toBeInTheDocument()
    expect(screen.getByLabelText(`Φεβρουάριος ${year}: 136,40 €`)).toBeInTheDocument()
    expect(screen.getByText('Ενοίκιο', { selector: 'p' })).toBeInTheDocument()

    const quarters = within(screen.getByRole('table', { name: 'ΦΠΑ ανά τρίμηνο' }))
    expect(quarters.getByText('26,40 €')).toBeInTheDocument()
    expect(quarters.getByText('7,70 €')).toBeInTheDocument()
  })

  it('shows the months as a table on request', async () => {
    serve()
    const { user } = renderPage()
    await screen.findByText('194,10 €')

    await user.click(screen.getByRole('switch', { name: 'Πίνακας' }))

    const months = within(screen.getByRole('table', { name: 'Έξοδα ανά μήνα' }))
    expect(months.getAllByRole('row')).toHaveLength(13)
    expect(months.getByText('Νοέμβριος').closest('tr')).toHaveTextContent('57,70 €')
  })

  it('lists the expense book and filters it by category', async () => {
    serve()
    const { user } = renderPage()

    const book = within(await screen.findByRole('table', { name: 'Βιβλίο εξόδων' }))
    expect(await book.findByText('124,00 €')).toBeInTheDocument()

    await user.click(screen.getByRole('combobox', { name: 'Κατηγορία' }))
    await user.click(await screen.findByRole('option', { name: 'Καύσιμα' }))

    expect(await book.findByText('Τα παραστατικά που επιβεβαιώνετε εμφανίζονται εδώ.')).toBeInTheDocument()
  })

  it('moves between years but not into the future', async () => {
    serve()
    const { user } = renderPage()
    await screen.findByText('194,10 €')

    expect(screen.getByRole('button', { name: 'Επόμενη χρονιά' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Προηγούμενη χρονιά' }))

    expect(screen.getByText(String(year - 1))).toBeInTheDocument()
  })
})

describe('niceMax', () => {
  it('rounds the axis up to clean steps', () => {
    expect(niceMax(136.4)).toEqual({ max: 150, step: 50 })
    expect(niceMax(980)).toEqual({ max: 1000, step: 500 })
    expect(niceMax(0)).toEqual({ max: 4, step: 1 })
  })
})
