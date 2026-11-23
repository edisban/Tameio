import { expect, test } from '@playwright/test'
import { changePassword, signIn, signInAsAdmin } from './support'

test('an administrator adds a manager, who must choose a password at first sign-in', async ({ page, browser }) => {
  await signInAsAdmin(page)
  await page.getByRole('link', { name: 'Προσωπικό' }).click()

  const email = `manager-${crypto.randomUUID()}@bank.example`
  await page.getByRole('button', { name: 'Νέο μέλος' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Ονοματεπώνυμο').fill('Emma Testwood')
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
  await page.getByRole('row').filter({ hasText: email }).getByRole('switch', { name: 'Ενεργός λογαριασμός: Emma Testwood' }).click()
  await expect(page.getByRole('row').filter({ hasText: email })).toContainText('Ανενεργός')
  await manager.reload()
  await expect(manager).toHaveURL(/\/sign-in$/)
})
