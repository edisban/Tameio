// How the app writes money, dates and rates: Greek conventions, in one place for every feature

const moneyFormat = new Intl.NumberFormat('el-GR', { style: 'currency', currency: 'EUR' })
const dateTimeFormat = new Intl.DateTimeFormat('el-GR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Athens' })
const dayFormat = new Intl.DateTimeFormat('el-GR', { dateStyle: 'medium', timeZone: 'UTC' })
const longDayFormat = new Intl.DateTimeFormat('el-GR', { dateStyle: 'long', timeZone: 'UTC' })
const rateFormat = new Intl.NumberFormat('el-GR', { style: 'percent', maximumFractionDigits: 2 })

/** 1234.5 as 1.234,50 € */
export function formatMoney(amount: number): string {
  return moneyFormat.format(amount)
}

/** A moment, in Athens time: 21 Νοε 2026, 10:05 */
export function formatDateTime(iso: string): string {
  return dateTimeFormat.format(new Date(iso))
}

/** A calendar date such as 2026-11-14, shown without shifting it through a time zone. */
export function formatDay(isoDate: string): string {
  return dayFormat.format(new Date(`${isoDate}T00:00:00Z`))
}

/** The same, with the month in words: 20 Δεκεμβρίου 2026 */
export function formatLongDay(isoDate: string): string {
  return longDayFormat.format(new Date(`${isoDate}T00:00:00Z`))
}

/** 0.07 as 7% */
export function formatRate(rate: number): string {
  return rateFormat.format(rate)
}
