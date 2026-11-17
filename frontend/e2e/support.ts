import { expect, type Page } from '@playwright/test'
import path from 'node:path'

/** Demo users from infra/keycloak/tameio-realm.json. */
export const users = {
  owner: { username: 'maria', password: 'maria' },
  analyst: { username: 'giorgos', password: 'giorgos' },
} as const

export const fixture = (name: string) => path.join(import.meta.dirname, 'fixtures', name)

/** Opens the app and signs in on the Keycloak page it redirects to. */
export async function signIn(page: Page, user: { username: string; password: string }) {
  await page.goto('/')
  await page.waitForURL(/\/realms\/tameio\//)
  // Keycloak's own page: its field ids stay the same in every language, unlike the labels
  await page.locator('#username').fill(user.username)
  await page.locator('#password').fill(user.password)
  await page.locator('#kc-login').click()
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
