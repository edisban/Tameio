/**
 * Amounts as people type them in Greece: "1.234,50", "1234,5" or "1234.50". Returns the amount in
 * cents, so sums are exact, or null if the text is not an amount.
 */
export function parseCents(input: string): number | null {
  const text = input.replace(/\s|€/g, '')
  if (!text) return null
  let normalised: string
  if (text.includes(',')) {
    // Greek format: dots group thousands, the comma is the decimal point
    normalised = text.replace(/\./g, '').replace(',', '.')
  } else {
    // A single dot followed by one or two digits is a decimal point; otherwise dots group thousands
    normalised = /^\d+\.\d{1,2}$/.test(text) ? text : text.replace(/\./g, '')
  }
  if (!/^\d+(\.\d{1,2})?$/.test(normalised)) return null
  const [units, decimals = ''] = normalised.split('.')
  return Number(units) * 100 + Number(decimals.padEnd(2, '0'))
}

/** Cents as the API takes them: 1234.5 for 123450. */
export const centsToAmount = (cents: number) => cents / 100

/** An amount from the API as the form shows it: 12.4 as "12,40". */
export function amountToText(amount: number | null | undefined): string {
  if (amount == null) return ''
  return amount.toFixed(2).replace('.', ',')
}
