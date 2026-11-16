import { describe, expect, it } from 'vitest'
import { isValidAfm } from './afm'

describe('isValidAfm', () => {
  it.each(['090000045', '094014201', '998123451', '094 014 201'])('accepts %s', afm => {
    expect(isValidAfm(afm)).toBe(true)
  })

  it.each(['123456789', '090000046', '000000000', '09000004', '0900000450', '09000004A', ''])('rejects %s', afm => {
    expect(isValidAfm(afm)).toBe(false)
  })
})
