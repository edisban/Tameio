/**
 * Greek tax number (ΑΦΜ) check, the same rule the API applies, so a typo is caught before submitting.
 * The first eight digits are weighted by 2^8 … 2^1; the sum mod 11 mod 10 must equal the ninth digit.
 */
export function isValidAfm(input: string): boolean {
  const afm = input.replace(/\s+/g, '')
  if (!/^\d{9}$/.test(afm) || afm === '000000000') return false
  let sum = 0
  for (let i = 0; i < 8; i++) sum += Number(afm[i]) * 2 ** (8 - i)
  return (sum % 11) % 10 === Number(afm[8])
}
