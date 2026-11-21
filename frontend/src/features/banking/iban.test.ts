import { describe, expect, it } from 'vitest'
import { formatIban, isValidIban } from './iban'

describe('IBAN', () => {
  it.each(['GR1601101250000000012300695', 'gr16 0110 1250 0000 0001 2300 695', 'DE89370400440532013000'])('accepts %s', iban => {
    expect(isValidIban(iban)).toBe(true)
  })

  it.each(['GR1601101250000000012300696', 'GR160110125000000001230069', 'GR16', ''])('refuses %j', iban => {
    expect(isValidIban(iban)).toBe(false)
  })

  it('prints in groups of four', () => {
    expect(formatIban('gr1601101250000000012300695')).toBe('GR16 0110 1250 0000 0001 2300 695')
  })
})
