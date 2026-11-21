import { expect, test, type Browser } from '@playwright/test'
import { registerBusiness, signInAsDemoStaff, signUp } from './support'

const newPage = async (browser: Browser) => (await browser.newContext({ locale: 'el-GR' })).newPage()

test('a business applies, an analyst recommends, a manager approves and pays the loan into the account', async ({ page, browser }) => {
  test.setTimeout(90_000)
  const name = `Εργαστήριο ${crypto.randomUUID().slice(0, 8)}`
  await signUp(page)
  const afm = await registerBusiness(page, name)
  await page.getByRole('link', { name: new RegExp(`ΑΦΜ ${afm}`) }).click()
  await page.getByRole('tab', { name: 'Λογαριασμοί' }).click()
  await page.getByRole('button', { name: 'Άνοιγμα λογαριασμού' }).click()
  const card = page.getByRole('button', { name: /^Λογαριασμός όψεως, υπόλοιπο/ })
  await expect(card).toHaveAccessibleName(/0,00/)

  // The business: a draft, then submitted to the bank
  await page.getByRole('tab', { name: 'Δάνεια' }).click()
  await page.getByRole('button', { name: 'Νέα αίτηση' }).click()
  await page.getByRole('dialog').getByLabel('Ποσό (€)').fill('10.000')
  await page.getByRole('dialog').getByRole('button', { name: 'Αποθήκευση πρόχειρης' }).click()
  await page.getByRole('button', { name: 'Υποβολή στην τράπεζα' }).click()
  await expect(page.getByRole('button', { name: 'Υποβολή στην τράπεζα' })).toHaveCount(0)

  // The analyst takes the case and recommends it
  const analyst = await newPage(browser)
  await signInAsDemoStaff(analyst, 'analyst')
  await analyst.getByRole('link', { name }).click()
  await analyst.getByRole('button', { name: 'Ανάληψη αξιολόγησης' }).click()
  await analyst.getByLabel('Αιτιολόγηση').fill('Νέα επιχείρηση, μικρό ποσό.')
  await analyst.getByRole('button', { name: 'Υποβολή εισήγησης' }).click()
  await expect(analyst.getByText('Εισήγηση: έγκριση')).toBeVisible()

  // A different person, the manager, decides and pays out
  const manager = await newPage(browser)
  await signInAsDemoStaff(manager, 'manager')
  await manager.getByRole('link', { name }).click()
  await manager.getByLabel('Αιτιολόγηση').fill('Εντός ορίου, με εισήγηση αναλυτή.')
  await manager.getByRole('button', { name: 'Οριστική απόφαση' }).click()
  await manager.getByRole('button', { name: /^Εκταμίευση/ }).click()
  await expect(manager.getByText('Εκταμίευση', { exact: true })).toBeVisible()

  // The business sees the loan paid and the money in the account
  await page.reload()
  await expect(page.getByText('Εκταμιεύτηκε').first()).toBeVisible()
  await page.getByRole('tab', { name: 'Λογαριασμοί' }).click()
  await expect(card).toHaveAccessibleName(/10\.000,00/)
})
