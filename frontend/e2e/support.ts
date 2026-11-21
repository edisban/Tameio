import { expect, type Page } from '@playwright/test'
import path from 'node:path'

export const fixture = (name: string) => path.join(import.meta.dirname, 'fixtures', name)

/**
 * Opens the app, which sends a signed-out visitor to the sign-in page, and signs up a new account.
 * Every test gets its own account, so tests running in parallel never see each other's data.
 */
export async function signUp(page: Page) {
  await page.goto('/')
  await page.waitForURL('/sign-in')
  await page.getByRole('link', { name: 'Εγγραφή' }).click()
  await page.getByLabel('Ονοματεπώνυμο').fill('Δοκιμαστικός Χρήστης')
  await page.getByLabel('Email').fill(`e2e-${crypto.randomUUID()}@example.com`)
  await page.getByLabel('Κωδικός').fill('e2e-password')
  await page.getByRole('button', { name: 'Δημιουργία λογαριασμού' }).click()
  await page.waitForURL('/businesses')
}

/** A valid ΑΦΜ no other test uses: eight random digits and the matching check digit. */
export function freshAfm(): string {
  const first = Array.from({ length: 8 }, () => Math.floor(Math.random() * 10)).join('')
  const sum = [...first].reduce((total, digit, i) => total + (Number(digit) << (8 - i)), 0)
  return first + ((sum % 11) % 10)
}

/** Registers a business with a fresh ΑΦΜ, so every test works on its own data. */
export async function registerBusiness(page: Page, name: string): Promise<string> {
  const afm = freshAfm()
  await page.getByRole('button', { name: 'Νέα επιχείρηση' }).click()
  await page.getByLabel('Επωνυμία').fill(name)
  await page.getByLabel('ΑΦΜ').fill(afm)
  await page.getByRole('button', { name: 'Καταχώριση' }).click()
  await expect(page.getByText(`ΑΦΜ ${afm}`)).toBeVisible()
  return afm
}

/**
 * The administrator comes from the demo data; a stack with only a bootstrap administrator (temporary
 * password) gets it replaced with E2E_ADMIN_PASSWORD on the first run.
 */
const ADMIN_EMAIL = 'admin@tameio.local'
// The demo administrator (compose loads demo data); the others are for stacks set up before it
const DEMO_ADMIN_PASSWORD = 'demo-staff-password-2026'
const BOOTSTRAP_PASSWORD = 'tameio-admin-2026'
const E2E_ADMIN_PASSWORD = 'e2e-admin-password-2026'

export async function signIn(page: Page, email: string, password: string) {
  await page.goto('/sign-in')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Κωδικός').fill(password)
  await page.getByRole('button', { name: 'Σύνδεση' }).click()
}

export async function changePassword(page: Page, current: string, next: string) {
  await expect(page).toHaveURL(/\/change-password$/)
  await page.getByLabel('Τωρινός κωδικός').fill(current)
  await page.getByLabel('Νέος κωδικός', { exact: true }).fill(next)
  await page.getByLabel('Νέος κωδικός ξανά').fill(next)
  await page.getByRole('button', { name: 'Αλλαγή κωδικού' }).click()
}

/**
 * Signs in as the administrator. On a fresh stack, tests running side by side may all find the
 * temporary password: one replaces it, and the others then sign in with the replacement.
 */
export async function signInAsAdmin(page: Page) {
  const wrong = page.getByText('Λάθος email ή κωδικός.')
  const landed = page.getByRole('heading', { name: 'Αιτήσεις δανείων' })
  const mustChange = page.getByRole('button', { name: 'Αλλαγή κωδικού' })
  for (const password of [DEMO_ADMIN_PASSWORD, E2E_ADMIN_PASSWORD, BOOTSTRAP_PASSWORD, E2E_ADMIN_PASSWORD]) {
    await signIn(page, ADMIN_EMAIL, password)
    await expect(wrong.or(landed).or(mustChange)).toBeVisible()
    if (await landed.isVisible()) return
    if (await mustChange.isVisible()) {
      await changePassword(page, password, E2E_ADMIN_PASSWORD)
      await expect(wrong.or(landed).or(page.getByText('Ο τωρινός κωδικός είναι λάθος.'))).toBeVisible()
      if (await landed.isVisible()) return
    }
  }
  throw new Error('Could not sign in as the administrator')
}
