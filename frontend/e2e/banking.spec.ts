import { expect, test } from '@playwright/test'
import { registerBusiness, signInAsAdmin, signUp } from './support'

test('a business opens an account and receives money; the bank books still balance', async ({ page, browser }) => {
  await signUp(page)
  const afm = await registerBusiness(page, 'Ταβέρνα Τράπεζας')
  await page.getByRole('link', { name: new RegExp(`ΑΦΜ ${afm}`) }).click()
  await page.getByRole('tab', { name: 'Λογαριασμοί' }).click()

  await page.getByRole('button', { name: 'Άνοιγμα λογαριασμού' }).click()
  const card = page.getByRole('button', { name: /^Λογαριασμός όψεως, υπόλοιπο/ })
  await expect(card).toHaveAccessibleName(/0,00/)
  const iban = (await card.locator('p').filter({ hasText: /^GR\d{2} 9990/ }).textContent())!.trim()

  await page.getByRole('button', { name: 'Κατάθεση demo' }).click()
  await page.getByRole('dialog').getByLabel('Ποσό (€)').fill('500')
  await page.getByRole('dialog').getByRole('button', { name: 'Κατάθεση' }).click()

  await expect(card).toHaveAccessibleName(/500,00/)
  await expect(page.getByRole('list', { name: 'Κινήσεις λογαριασμού' })).toContainText('+500,00')

  const staff = await (await browser.newContext({ locale: 'el-GR' })).newPage()
  await signInAsAdmin(staff)
  await staff.getByRole('link', { name: 'Γενικό καθολικό' }).click()
  await expect(staff.getByText('Ισοσκελισμένο')).toBeVisible()
  await staff.getByRole('link', { name: 'Λογαριασμοί πελατών' }).click()
  await staff.getByRole('textbox', { name: 'Αναζήτηση λογαριασμών' }).fill(iban.replace(/\s/g, ''))
  await expect(staff.getByRole('table', { name: 'Λογαριασμοί πελατών' })).toContainText('Ταβέρνα Τράπεζας')
})
