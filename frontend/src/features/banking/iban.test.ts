import { describe, expect, it } from 'vitest'
import { formatIban } from './iban'

describe('formatIban', () => {
  it('prints an IBAN in upper case, in groups of four', () => {
    expect(formatIban('gr16 01101250000000012300695')).toBe('GR16 0110 1250 0000 0001 2300 695')
  })
})
