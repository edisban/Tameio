import { ApiError } from '../../api/errors'

/** The same limits the API enforces, checked first in the browser so people get an answer at once. */
export const MAX_FILE_BYTES = 10 * 1024 * 1024
export const ACCEPTED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'] as const
export const ACCEPT_ATTRIBUTE = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'

/** Why this file cannot be uploaded, or null if it can. The server checks the real content again. */
export function fileProblem(file: { size: number; type: string }): string | null {
  if (file.size === 0) return 'Το αρχείο είναι κενό.'
  if (file.size > MAX_FILE_BYTES) return 'Το αρχείο ξεπερνά τα 10 MB.'
  if (!(ACCEPTED_TYPES as readonly string[]).includes(file.type)) return 'Επιτρέπονται μόνο PDF, JPEG και PNG.'
  return null
}

const uploadMessages: Record<number, string> = {
  409: 'Αυτό το αρχείο έχει ήδη ανέβει.',
  413: 'Το αρχείο ξεπερνά τα 10 MB.',
  415: 'Το περιεχόμενο δεν είναι PDF, JPEG ή PNG.',
}

/** A message for the person uploading, in Greek, for the answers the API is known to give. */
export function uploadErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return uploadMessages[error.problem.status] ?? error.message
  }
  return 'Δεν ήταν δυνατή η σύνδεση με τον διακομιστή.'
}

const sizeFormat = new Intl.NumberFormat('el-GR', { maximumFractionDigits: 1 })

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${sizeFormat.format(bytes / 1024)} KB`
  return `${sizeFormat.format(bytes / (1024 * 1024))} MB`
}

const typeLabels: Record<string, string> = {
  'application/pdf': 'PDF',
  'image/jpeg': 'JPEG',
  'image/png': 'PNG',
}

export function typeLabel(mediaType: string): string {
  return typeLabels[mediaType] ?? mediaType
}
