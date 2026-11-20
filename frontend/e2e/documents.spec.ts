import { expect, test } from '@playwright/test'
import { fixture, registerBusiness, signUp } from './support'

test.beforeEach(async ({ page }) => {
  await signUp(page)
  const afm = await registerBusiness(page, 'Καφέ Δοκιμής')
  await page.getByRole('link', { name: new RegExp(`ΑΦΜ ${afm}`) }).click()
  await expect(page.getByRole('heading', { name: 'Καφέ Δοκιμής' })).toBeVisible()
  await expect(page.getByText('Κανένα παραστατικό ακόμα')).toBeVisible()
})

test('uploads a PDF and a photo, and refuses a file that only pretends to be a PDF', async ({ page }) => {
  await page.getByLabel('Επιλογή αρχείων').setInputFiles([fixture('receipt.pdf'), fixture('receipt.png'), fixture('not-a-pdf.pdf')])

  const table = page.getByRole('table', { name: 'Παραστατικά' })
  await expect(table.getByRole('link', { name: 'receipt.pdf' })).toBeVisible()
  await expect(table.getByRole('link', { name: 'receipt.png' })).toBeVisible()
  await expect(table.getByRole('link', { name: 'not-a-pdf.pdf' })).toHaveCount(0)
  await expect(page.getByText('Το περιεχόμενο δεν είναι PDF, JPEG ή PNG.')).toBeVisible()
})

test('refuses the same file twice', async ({ page }) => {
  await page.getByLabel('Επιλογή αρχείων').setInputFiles(fixture('receipt.pdf'))
  await expect(page.getByRole('table', { name: 'Παραστατικά' }).getByRole('link', { name: 'receipt.pdf' })).toBeVisible()

  await page.getByLabel('Επιλογή αρχείων').setInputFiles(fixture('receipt.pdf'))

  await expect(page.getByText('Αυτό το αρχείο έχει ήδη ανέβει.')).toBeVisible()
})

test('opens an uploaded document in a new tab from its page', async ({ page, context }) => {
  await page.getByLabel('Επιλογή αρχείων').setInputFiles(fixture('receipt.png'))
  await page.getByRole('table', { name: 'Παραστατικά' }).getByRole('link', { name: 'receipt.png' }).click()
  await expect(page.getByRole('heading', { name: 'receipt.png' })).toBeVisible()
  await expect(page.getByRole('img', { name: 'Προεπισκόπηση: receipt.png' })).toBeVisible()

  const [tab] = await Promise.all([context.waitForEvent('page'), page.getByRole('button', { name: 'Άνοιγμα αρχείου' }).click()])

  await tab.waitForURL(/^blob:/)
  await expect(tab.locator('img')).toBeVisible()
})

test('document AI reads an uploaded receipt and the list shows what it found', async ({ page }) => {
  // Real OCR, shared with the other tests running at the same time
  test.setTimeout(90_000)
  await page.getByLabel('Επιλογή αρχείων').setInputFiles(fixture('receipt.pdf'))
  const row = page.getByRole('row').filter({ has: page.getByRole('link', { name: 'receipt.pdf' }) })

  // Through the outbox, RabbitMQ and real OCR; the list polls until the answer is in
  await expect(row.getByText('Διαβάστηκε')).toBeVisible({ timeout: 60_000 })
  await expect(row.getByText(/€/)).toBeVisible()
})

test('the owner checks what was read, corrects the category and confirms it', async ({ page }) => {
  test.setTimeout(90_000)
  await page.getByLabel('Επιλογή αρχείων').setInputFiles(fixture('receipt.pdf'))
  const row = page.getByRole('row').filter({ has: page.getByRole('link', { name: 'receipt.pdf' }) })
  await expect(row.getByText('Διαβάστηκε')).toBeVisible({ timeout: 60_000 })
  await row.getByRole('link', { name: 'receipt.pdf' }).click()

  // The fixture is a bare till receipt: document AI finds the total, the owner fills in what it lacks
  const form = page.getByRole('form', { name: 'Έλεγχος παραστατικού' })
  await expect(form.getByLabel('Σύνολο')).toHaveValue('4,00')
  await form.getByLabel('ΑΦΜ εκδότη').fill('094014201')
  await form.getByLabel('Ημερομηνία έκδοσης').fill('2026-11-14')
  await form.getByLabel('Καθαρό').fill('3,23')
  await form.getByLabel('ΦΠΑ').fill('0,77')
  await form.getByRole('combobox', { name: 'Κατηγορία εξόδου' }).click()
  await page.getByRole('option', { name: 'Εξοπλισμός' }).click()
  await form.getByRole('button', { name: 'Επιβεβαίωση' }).click()

  await expect(page.getByText('Αποθηκεύτηκε.')).toBeVisible()
  await page.getByRole('link', { name: 'Καφέ Δοκιμής' }).click()
  await expect(row.getByText('Ελέγχθηκε')).toBeVisible()
  await expect(row.getByText('Εξοπλισμός')).toBeVisible()
})
