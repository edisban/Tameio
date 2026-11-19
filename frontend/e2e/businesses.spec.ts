import { expect, test } from '@playwright/test'
import { registerBusiness, signUp } from './support'

test('an owner registers a business and sees it in the list', async ({ page }) => {
  await signUp(page)
  await expect(page.getByRole('heading', { name: 'Οι επιχειρήσεις μου' })).toBeVisible()

  const afm = await registerBusiness(page, 'Φούρνος Η Γωνιά')

  await expect(page.getByRole('link', { name: new RegExp(`Φούρνος Η Γωνιά.*ΑΦΜ ${afm}`) })).toBeVisible()
})

test('an invalid ΑΦΜ is caught before anything is sent', async ({ page }) => {
  await signUp(page)
  await page.getByRole('button', { name: 'Νέα επιχείρηση' }).click()
  await page.getByLabel('Επωνυμία').fill('Λάθος ΑΦΜ')
  await page.getByLabel('ΑΦΜ').fill('123456789')

  await page.getByRole('button', { name: 'Καταχώριση' }).click()

  await expect(page.getByText('Μη έγκυρο ΑΦΜ')).toBeVisible()
})
