import { expect, test, type Page } from '@playwright/test'

/**
 * The first administrator comes from the stack's settings (compose.yaml) with a temporary password.
 * The first run replaces it with E2E_ADMIN_PASSWORD; later runs sign in with that.
 */
const ADMIN_EMAIL = 'admin@tameio.local'
const BOOTSTRAP_PASSWORD = 'tameio-admin-2026'
const E2E_ADMIN_PASSWORD = 'e2e-admin-password-2026'

async function signIn(page: Page, email: string, password: string) {
  await page.goto('/sign-in')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Κωδικός').fill(password)
  await page.getByRole('button', { name: 'Σύνδεση' }).click()
}

async function changePassword(page: Page, current: string, next: string) {
  await expect(page).toHaveURL(/\/change-password$/)
  await page.getByLabel('Τωρινός κωδικός').fill(current)
  await page.getByLabel('Νέος κωδικός', { exact: true }).fill(next)
  await page.getByLabel('Νέος κωδικός ξανά').fill(next)
  await page.getByRole('button', { name: 'Αλλαγή κωδικού' }).click()
}

async function signInAsAdmin(page: Page) {
  await signIn(page, ADMIN_EMAIL, E2E_ADMIN_PASSWORD)
  const wrong = page.getByText('Λάθος email ή κωδικός.')
  const landed = page.getByRole('heading', { name: 'Αιτήσεις δανείων' })
  await expect(wrong.or(landed)).toBeVisible()
  if (await wrong.isVisible()) {
    await signIn(page, ADMIN_EMAIL, BOOTSTRAP_PASSWORD)
    await changePassword(page, BOOTSTRAP_PASSWORD, E2E_ADMIN_PASSWORD)
    await expect(landed).toBeVisible()
  }
}

test('an administrator adds a manager, who must choose a password at first sign-in', async ({ page, browser }) => {
  await signInAsAdmin(page)
  await page.getByRole('link', { name: 'Προσωπικό' }).click()

  const email = `manager-${crypto.randomUUID()}@bank.example`
  await page.getByRole('button', { name: 'Νέο μέλος' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Ονοματεπώνυμο').fill('Ελένη Δοκιμή')
  await dialog.getByLabel('Email').fill(email)
  await dialog.getByRole('combobox', { name: 'Ρόλος' }).click()
  await page.getByRole('option', { name: 'Διευθυντής πιστοδοτήσεων' }).click()
  await dialog.getByLabel('Όριο έγκρισης (€)').fill('75.000')
  await dialog.getByRole('button', { name: 'Δημιουργία' }).click()

  const shown = page.getByRole('dialog', { name: 'Προσωρινός κωδικός' })
  const temporary = (await shown.locator('code').textContent())!.trim()
  expect(temporary).toMatch(/^[A-Za-z2-9]{4}(-[A-Za-z2-9]{4}){3}$/)
  await shown.getByRole('button', { name: 'Έγινε' }).click()
  const row = page.getByRole('row').filter({ hasText: email })
  await expect(row).toContainText('75.000,00 €')
  await expect(row).toContainText('Προσωρινός κωδικός')

  // The manager, in a browser of their own
  const manager = await (await browser.newContext({ locale: 'el-GR' })).newPage()
  await signIn(manager, email, temporary)
  await changePassword(manager, temporary, 'manager chosen password')
  await expect(manager.getByRole('heading', { name: 'Αιτήσεις δανείων' })).toBeVisible()
  await expect(manager.getByRole('link', { name: 'Προσωπικό' })).toHaveCount(0)

  // Disabling the account ends the manager's session at once
  await page.reload()
  await page.getByRole('switch', { name: 'Ενεργός λογαριασμός: Ελένη Δοκιμή' }).click()
  await expect(page.getByRole('row').filter({ hasText: email })).toContainText('Ανενεργός')
  await manager.reload()
  await expect(manager).toHaveURL(/\/sign-in$/)
})
