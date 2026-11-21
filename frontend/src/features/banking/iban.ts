/** IBANs as people read them: upper case, in groups of four. */

export const normaliseIban = (input: string) => input.replace(/\s+/g, '').toUpperCase()

/** In groups of four, as printed on cards and statements. */
export const formatIban = (input: string) => normaliseIban(input).replace(/(.{4})(?!$)/g, '$1 ')
