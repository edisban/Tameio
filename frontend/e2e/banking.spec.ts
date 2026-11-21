import { expect, test } from '@playwright/test'
import { registerBusiness, signInAsAdmin, signUp } from './support'

test('a business opens accounts, receives money and pays; the bank books still balance', async ({ page, browser }) => {
  await signUp(page)
  const afm = await registerBusiness(page, 'Ταβέρνα Τράπεζας')
  await page.getByRole('link', { name: new RegExp(`ΑΦΜ ${afm}`) }).click()
  await page.getByRole('tab', { name: 'Λογαριασμοί' }).click()

  await page.getByRole('button', { name: 'Άνοιγμα λογαριασμού' }).click()
  await page.getByRole('button', { name: 'Νέος λογαριασμός' }).click()
  const cards = page.getByRole('button', { name: /^Λογαριασμός όψεως, υπόλοιπο/ })
  await expect(cards).toHaveCount(2)

  // The second card's IBAN is the payee
  const payee = (await cards.nth(1).locator('p').filter({ hasText: /^GR\d{2} 9990/ }).textContent())!.trim()
  await cards.nth(0).click()
  await page.getByRole('button', { name: 'Κατάθεση demo' }).click()
  await page.getByRole('dialog').getByLabel('Ποσό (€)').fill('500')
  await page.getByRole('dialog').getByRole('button', { name: 'Κατάθεση' }).click()
  await expect(cards.nth(0)).toHaveAccessibleName(/500,00/)

  await page.getByRole('button', { name: 'Νέα μεταφορά' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('IBAN δικαιούχου').fill(payee)
  await dialog.getByLabel('Όνομα δικαιούχου').fill('Ταβέρνα Τράπεζας')
  await dialog.getByLabel('Ποσό (€)').fill('123,45')
  await dialog.getByLabel('Αιτιολογία').fill('Μεταφορά ταμείου')
  await dialog.getByRole('button', { name: 'Συνέχεια' }).click()
  await dialog.getByRole('button', { name: /Πληρωμή 123,45/ }).click()
  await expect(page.getByText('Η πληρωμή ολοκληρώθηκε')).toBeVisible()
  await page.getByRole('button', { name: 'Κλείσιμο' }).click()

  await expect(cards.nth(0)).toHaveAccessibleName(/376,55/)
  await expect(page.getByRole('list', { name: 'Κινήσεις λογαριασμού' })).toContainText('Μεταφορά ταμείου')
  await cards.nth(1).click()
  await expect(cards.nth(1)).toHaveAccessibleName(/123,45/)
  await expect(page.getByRole('list', { name: 'Κινήσεις λογαριασμού' })).toContainText('+123,45')

  const staff = await (await browser.newContext({ locale: 'el-GR' })).newPage()
  await signInAsAdmin(staff)
  await staff.getByRole('link', { name: 'Γενικό καθολικό' }).click()
  await expect(staff.getByText('Ισοσκελισμένο')).toBeVisible()
  await staff.getByRole('link', { name: 'Λογαριασμοί πελατών' }).click()
  await staff.getByRole('textbox', { name: 'Αναζήτηση λογαριασμών' }).fill(payee.replace(/\s/g, ''))
  await expect(staff.getByRole('table', { name: 'Λογαριασμοί πελατών' })).toContainText('Ταβέρνα Τράπεζας')
})

test('a SEPA payment to another bank clears, and one to a closed account comes back', async ({ page }) => {
  test.setTimeout(90_000)
  await signUp(page)
  const afm = await registerBusiness(page, 'Εμπόριο SEPA')
  await page.getByRole('link', { name: new RegExp(`ΑΦΜ ${afm}`) }).click()
  await page.getByRole('tab', { name: 'Λογαριασμοί' }).click()
  await page.getByRole('button', { name: 'Άνοιγμα λογαριασμού' }).click()
  await page.getByRole('button', { name: 'Κατάθεση demo' }).click()
  await page.getByRole('dialog').getByLabel('Ποσό (€)').fill('1000')
  await page.getByRole('dialog').getByRole('button', { name: 'Κατάθεση' }).click()
  const card = page.getByRole('button', { name: /^Λογαριασμός όψεως, υπόλοιπο/ })
  await expect(card).toHaveAccessibleName(/1\.000,00/)

  async function payDemoPayee(payee: RegExp, amount: string) {
    await page.getByRole('button', { name: 'Νέα μεταφορά' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('combobox', { name: 'Δικαιούχοι demo σε άλλες τράπεζες' }).click()
    await page.getByRole('option', { name: payee }).click()
    await dialog.getByLabel('Ποσό (€)').fill(amount)
    await dialog.getByRole('button', { name: 'Συνέχεια' }).click()
    await expect(dialog.getByText('Το όνομα ταιριάζει με τον κάτοχο του λογαριασμού.')).toBeVisible()
    await expect(dialog.getByText(/SEPA, προς άλλη τράπεζα/)).toBeVisible()
    await dialog.getByRole('button', { name: /Πληρωμή/ }).click()
    await expect(page.getByText('Η πληρωμή στάλθηκε')).toBeVisible()
    await page.getByRole('button', { name: 'Κλείσιμο' }).click()
  }

  const payments = page.getByRole('list', { name: 'Πληρωμές' })
  await payDemoPayee(/ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ/, '150')
  await expect(card).toHaveAccessibleName(/850,00/)
  await expect(payments.getByRole('listitem').first()).toContainText('Εκκαθαρίστηκε', { timeout: 20_000 })

  await payDemoPayee(/ΠΑΛΙΟ ΚΑΤΑΣΤΗΜΑ/, '200')
  await expect(payments.getByRole('listitem').first()).toContainText('Επιστράφηκε', { timeout: 20_000 })
  // The money came back
  await expect(card).toHaveAccessibleName(/850,00/)
  await expect(page.getByRole('list', { name: 'Κινήσεις λογαριασμού' }).getByRole('listitem').first()).toContainText('+200,00')
})
