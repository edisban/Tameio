import { describe, expect, it } from 'vitest'
import { amountToText, parseCents } from './amounts'

describe('parseCents', () => {
  it.each([
    ['12,40', 1240],
    ['12,4', 1240],
    ['1.234,50', 123450],
    ['1234.50', 123450],
    ['1.234', 123400],
    ['0', 0],
    [' 7,00 € ', 700],
  ])('reads %s as %i cents', (input, cents) => {
    expect(parseCents(input)).toBe(cents)
  })

  it.each(['', 'abc', '12,345', '-5', '1,2,3'])('refuses %j', input => {
    expect(parseCents(input)).toBeNull()
  })
})

describe('amountToText', () => {
  it('shows two decimals with a comma', () => {
    expect(amountToText(12.4)).toBe('12,40')
    expect(amountToText(null)).toBe('')
  })
})
