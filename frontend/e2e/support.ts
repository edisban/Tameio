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
