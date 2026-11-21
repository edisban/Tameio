import type { LoanPurpose, LoanStatus, RiskBand, RiskFactor } from './api'

export const statusLabel: Record<LoanStatus, string> = {
  DRAFT: 'Πρόχειρη',
  SUBMITTED: 'Υποβλήθηκε',
  IN_REVIEW: 'Σε αξιολόγηση',
  RECOMMENDED: 'Με εισήγηση',
  APPROVED: 'Εγκρίθηκε',
  DECLINED: 'Απορρίφθηκε',
  DISBURSED: 'Εκταμιεύτηκε',
}

export const statusColour: Record<LoanStatus, { bgcolor: string; color: string }> = {
  DRAFT: { bgcolor: 'action.hover', color: 'text.secondary' },
  SUBMITTED: { bgcolor: 'primary.light', color: 'primary.main' },
  IN_REVIEW: { bgcolor: 'primary.light', color: 'primary.main' },
  RECOMMENDED: { bgcolor: 'warning.light', color: 'warning.main' },
  APPROVED: { bgcolor: 'success.light', color: 'success.main' },
  DECLINED: { bgcolor: 'error.light', color: 'error.main' },
  DISBURSED: { bgcolor: 'success.main', color: '#ffffff' },
}

/** The steps an application goes through, in order, for a progress bar. */
export const journey: LoanStatus[] = ['SUBMITTED', 'IN_REVIEW', 'RECOMMENDED', 'APPROVED', 'DISBURSED']

export const purposeLabel: Record<LoanPurpose, string> = {
  WORKING_CAPITAL: 'Κεφάλαιο κίνησης',
  EQUIPMENT: 'Εξοπλισμός',
  RENOVATION: 'Ανακαίνιση',
  EXPANSION: 'Επέκταση',
}

export const bandLabel: Record<RiskBand, string> = { LOW: 'Χαμηλός κίνδυνος', MEDIUM: 'Μέτριος κίνδυνος', HIGH: 'Υψηλός κίνδυνος' }

export const bandColour: Record<RiskBand, { bgcolor: string; color: string }> = {
  LOW: { bgcolor: 'success.light', color: 'success.main' },
  MEDIUM: { bgcolor: 'warning.light', color: 'warning.main' },
  HIGH: { bgcolor: 'error.light', color: 'error.main' },
}

const percent = new Intl.NumberFormat('el-GR', { style: 'percent', maximumFractionDigits: 0 })
const decimal = new Intl.NumberFormat('el-GR', { maximumFractionDigits: 1 })

/** Each scorecard factor in words, with what was measured. */
export function explain(factor: RiskFactor): { title: string; detail: string } {
  const m = factor.measured
  switch (factor.code) {
    case 'REPAYMENT_CAPACITY':
      return {
        title: 'Ικανότητα αποπληρωμής',
        detail: m === null ? 'Η επιχείρηση δεν έχει μηνιαίο πλεόνασμα.' : `Η δόση είναι το ${percent.format(m)} του μηνιαίου πλεονάσματος.`,
      }
    case 'TRACK_RECORD':
      return { title: 'Ιστορικό λογαριασμού', detail: `${decimal.format(m ?? 0)} μήνες κινήσεων.` }
    case 'CASH_CUSHION':
      return {
        title: 'Αποθεματικό',
        detail: m === null ? 'Δεν υπολογίζεται.' : `Το υπόλοιπο καλύπτει ${decimal.format(m)} δόσεις.`,
      }
    case 'LOAN_SIZE':
      return {
        title: 'Μέγεθος δανείου',
        detail: m === null ? 'Δεν υπάρχουν εισροές.' : `Το δάνειο είναι το ${percent.format(m)} των ετήσιων εισροών.`,
      }
  }
}
