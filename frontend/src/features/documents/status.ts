import type { DocumentStatus, ReadFailure } from './api'

export const statusLabel: Record<DocumentStatus, string> = {
  UPLOADED: 'Σε αναμονή ανάγνωσης',
  READING: 'Διαβάζεται',
  READ: 'Διαβάστηκε',
  FAILED: 'Δεν διαβάστηκε',
  REVIEWED: 'Ελέγχθηκε',
}

export const statusColour: Record<DocumentStatus, { bgcolor: string; color: string }> = {
  UPLOADED: { bgcolor: 'warning.light', color: 'warning.main' },
  READING: { bgcolor: 'primary.light', color: 'primary.main' },
  READ: { bgcolor: 'success.light', color: 'success.main' },
  FAILED: { bgcolor: 'error.light', color: 'error.main' },
  REVIEWED: { bgcolor: 'success.main', color: '#ffffff' },
}

export const failureLabel: Record<ReadFailure, string> = {
  UNREADABLE: 'Δεν βρέθηκε κείμενο. Δοκιμάστε μια πιο καθαρή φωτογραφία ή σάρωση.',
  REJECTED: 'Η υπηρεσία ανάγνωσης δεν δέχτηκε το αρχείο.',
  GAVE_UP: 'Η υπηρεσία ανάγνωσης δεν απάντησε. Δοκιμάστε ξανά σε λίγο.',
}
