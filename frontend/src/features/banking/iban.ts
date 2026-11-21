/** IBANs as the server checks them (ISO 13616 mod 97), so mistakes are caught while typing. */

export const normaliseIban = (input: string) => input.replace(/\s+/g, '').toUpperCase()

const LENGTHS: Record<string, number> = { GR: 27, CY: 28, DE: 22, FR: 27, IT: 27, ES: 24, NL: 18, BE: 16, AT: 20, PT: 25, IE: 22 }

export function isValidIban(input: string): boolean {
  const iban = normaliseIban(input)
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban)) return false
  const length = LENGTHS[iban.slice(0, 2)]
  if (length && iban.length !== length) return false
  const digits = (iban.slice(4) + iban.slice(0, 4)).replace(/[A-Z]/g, c => String(c.charCodeAt(0) - 55))
  return BigInt(digits) % 97n === 1n
}

/** In groups of four, as printed on cards and statements. */
export const formatIban = (input: string) => normaliseIban(input).replace(/(.{4})(?!$)/g, '$1 ')
