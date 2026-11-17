import { expect, test } from '@playwright/test'
import { fixture, registerBusiness, signIn, users } from './support'

test.beforeEach(async ({ page }) => {
  await signIn(page, users.owner)
  // Tests run in parallel as the same user, so each one opens its own business by its unique ΑΦΜ
  const afm = await registerBusiness(page, 'Καφέ Δοκιμής')
  await page.getByRole('link', { name: new RegExp(`ΑΦΜ ${afm}`) }).click()
  await expect(page.getByRole('heading', { name: 'Καφέ Δοκιμής' })).toBeVisible()
  await expect(page.getByText('Κανένα παραστατικό ακόμα')).toBeVisible()
})

test('uploads a PDF and a photo, and refuses a file that only pretends to be a PDF', async ({ page }) => {
  await page.getByLabel('Επιλογή αρχείων').setInputFiles([fixture('receipt.pdf'), fixture('receipt.png'), fixture('not-a-pdf.pdf')])

  const table = page.getByRole('table', { name: 'Παραστατικά' })
  await expect(table.getByRole('button', { name: 'receipt.pdf' })).toBeVisible()
  await expect(table.getByRole('button', { name: 'receipt.png' })).toBeVisible()
  await expect(table.getByRole('button', { name: 'not-a-pdf.pdf' })).toHaveCount(0)
  await expect(page.getByText('Το περιεχόμενο δεν είναι PDF, JPEG ή PNG.')).toBeVisible()
})

test('refuses the same file twice', async ({ page }) => {
  await page.getByLabel('Επιλογή αρχείων').setInputFiles(fixture('receipt.pdf'))
  await expect(page.getByRole('table', { name: 'Παραστατικά' }).getByRole('button', { name: 'receipt.pdf' })).toBeVisible()

  await page.getByLabel('Επιλογή αρχείων').setInputFiles(fixture('receipt.pdf'))

  await expect(page.getByText('Αυτό το αρχείο έχει ήδη ανέβει.')).toBeVisible()
})

test('opens an uploaded document in a new tab', async ({ page, context }) => {
  await page.getByLabel('Επιλογή αρχείων').setInputFiles(fixture('receipt.png'))
  const link = page.getByRole('table', { name: 'Παραστατικά' }).getByRole('button', { name: 'receipt.png' })

  const [tab] = await Promise.all([context.waitForEvent('page'), link.click()])

  await tab.waitForURL(/^blob:/)
  await expect(tab.locator('img')).toBeVisible()
})
