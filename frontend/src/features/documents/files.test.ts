import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api/errors'
import { fileProblem, formatBytes, MAX_FILE_BYTES, uploadErrorMessage } from './files'

describe('fileProblem', () => {
  it('accepts a PDF, a JPEG and a PNG', () => {
    expect(fileProblem({ size: 2048, type: 'application/pdf' })).toBeNull()
    expect(fileProblem({ size: 2048, type: 'image/jpeg' })).toBeNull()
    expect(fileProblem({ size: 2048, type: 'image/png' })).toBeNull()
  })

  it('rejects an empty file', () => {
    expect(fileProblem({ size: 0, type: 'application/pdf' })).toBe('Το αρχείο είναι κενό.')
  })

  it('rejects a file over 10 MB but accepts exactly 10 MB', () => {
    expect(fileProblem({ size: MAX_FILE_BYTES, type: 'application/pdf' })).toBeNull()
    expect(fileProblem({ size: MAX_FILE_BYTES + 1, type: 'application/pdf' })).toBe('Το αρχείο ξεπερνά τα 10 MB.')
  })

  it('rejects other types', () => {
    expect(fileProblem({ size: 10, type: 'application/vnd.ms-excel' })).toBe('Επιτρέπονται μόνο PDF, JPEG και PNG.')
    expect(fileProblem({ size: 10, type: '' })).toBe('Επιτρέπονται μόνο PDF, JPEG και PNG.')
  })
})

describe('uploadErrorMessage', () => {
  it('explains the known API answers in Greek', () => {
    expect(uploadErrorMessage(new ApiError({ status: 415, detail: 'Upload a PDF, JPEG or PNG file.' }))).toBe(
      'Το περιεχόμενο δεν είναι PDF, JPEG ή PNG.',
    )
    expect(uploadErrorMessage(new ApiError({ status: 409 }))).toBe('Αυτό το αρχείο έχει ήδη ανέβει.')
  })

  it('falls back to the API detail, and to a network message for anything else', () => {
    expect(uploadErrorMessage(new ApiError({ status: 500, detail: 'Server error' }))).toBe('Server error')
    expect(uploadErrorMessage(new TypeError('Failed to fetch'))).toBe('Δεν ήταν δυνατή η σύνδεση με τον διακομιστή.')
  })
})

describe('formatBytes', () => {
  it('uses the unit that keeps the number short, with a Greek decimal comma', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1536)).toBe('1,5 KB')
    expect(formatBytes(3 * 1024 * 1024)).toBe('3 MB')
  })
})
