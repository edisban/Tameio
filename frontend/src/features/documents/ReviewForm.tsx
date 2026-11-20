import { zodResolver } from '@hookform/resolvers/zod'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import ErrorRoundedIcon from '@mui/icons-material/ErrorRounded'
import { Alert, Button, Card, CardContent, List, ListItem, ListItemIcon, ListItemText, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../../api/http'
import { isValidAfm } from '../businesses/afm'
import { useReviewDocument, type DocumentDetails, type Found } from './api'
import { amountToText, centsToAmount, parseCents } from './amounts'
import { categoryLabels } from './categories'

/** Below this, a field is flagged for a closer look. */
const SURE_ENOUGH = 0.8

const amount = (label: string) =>
  z.string().refine(value => parseCents(value) !== null, `Γράψτε ${label} σε ευρώ, π.χ. 12,40`)

const today = () => new Date().toISOString().slice(0, 10)

const schema = z
  .object({
    issuerAfm: z.string().transform(value => value.replace(/\s+/g, '')).refine(isValidAfm, 'Μη έγκυρο ΑΦΜ'),
    issueDate: z
      .string()
      .min(1, 'Επιλέξτε την ημερομηνία έκδοσης')
      .refine(value => value <= today(), 'Η ημερομηνία δεν μπορεί να είναι μελλοντική'),
    netAmount: amount('το καθαρό ποσό'),
    vatAmount: amount('τον ΦΠΑ'),
    totalAmount: amount('το σύνολο'),
    category: z.string().min(1, 'Επιλέξτε κατηγορία'),
  })
  .refine(
    form => {
      const [net, vat, total] = [parseCents(form.netAmount), parseCents(form.vatAmount), parseCents(form.totalAmount)]
      return net === null || vat === null || total === null || net + vat === total
    },
    { path: ['totalAmount'], message: 'Καθαρό και ΦΠΑ δεν αθροίζουν στο σύνολο' },
  )

type FormInput = z.input<typeof schema>

const fieldNames: Record<string, keyof FormInput> = {
  issuerAfm: 'issuerAfm',
  issueDate: 'issueDate',
  netAmount: 'netAmount',
  vatAmount: 'vatAmount',
  totalAmount: 'totalAmount',
  category: 'category',
  balanced: 'totalAmount',
}

function startingValues(document: DocumentDetails): FormInput {
  const { review, reading } = document
  if (review) {
    return {
      issuerAfm: review.issuerAfm,
      issueDate: review.issueDate,
      netAmount: amountToText(review.netAmount),
      vatAmount: amountToText(review.vatAmount),
      totalAmount: amountToText(review.totalAmount),
      category: review.category,
    }
  }
  return {
    issuerAfm: reading?.issuerAfm?.value ?? '',
    issueDate: reading?.issueDate?.value ?? '',
    netAmount: amountToText(reading?.netAmount?.value),
    vatAmount: amountToText(reading?.vatAmount?.value),
    totalAmount: amountToText(reading?.totalAmount?.value),
    category: reading?.category.code && reading.category.code in categoryLabels ? reading.category.code : '',
  }
}

/** Under each field: how sure document AI was, so people know where to look twice. */
function confidenceHint(found: Found<unknown> | null | undefined, reviewed: boolean): { text: string; warn: boolean } | null {
  if (reviewed) return null
  if (!found) return { text: 'Δεν βρέθηκε στο έγγραφο', warn: true }
  const percent = Math.round(found.confidence * 100)
  return found.confidence < SURE_ENOUGH
    ? { text: `Ελέγξτε το: βεβαιότητα ${percent}%`, warn: true }
    : { text: `Βεβαιότητα ${percent}%`, warn: false }
}

// The checks document AI runs (ml/document-ai/src/document_ai/fields.py)
const checkLabels: Record<string, string> = {
  ISSUER_AFM_FOUND: 'Βρέθηκε έγκυρο ΑΦΜ εκδότη',
  AMOUNTS_ADD_UP: 'Καθαρό και ΦΠΑ αθροίζουν στο σύνολο',
  VAT_RATE_KNOWN: 'Ο ΦΠΑ αντιστοιχεί σε ελληνικό συντελεστή',
  TOTAL_FOUND: 'Βρέθηκε σύνολο',
}

export function ReviewForm({ businessId, document }: { businessId: string; document: DocumentDetails }) {
  const review = useReviewDocument(businessId, document.id)
  const form = useForm<FormInput>({ resolver: zodResolver(schema), values: startingValues(document) })
  const { errors, isSubmitting } = form.formState
  const reviewed = document.status === 'REVIEWED'
  const reading = document.reading

  const submit = form.handleSubmit(async values => {
    try {
      await review.mutateAsync({
        issuerAfm: values.issuerAfm.replace(/\s+/g, ''),
        issueDate: values.issueDate,
        netAmount: centsToAmount(parseCents(values.netAmount)!),
        vatAmount: centsToAmount(parseCents(values.vatAmount)!),
        totalAmount: centsToAmount(parseCents(values.totalAmount)!),
        category: values.category,
      })
    } catch (e) {
      if (!(e instanceof ApiError)) return
      for (const [field, message] of Object.entries(e.problem.errors ?? {})) {
        const name = fieldNames[field]
        if (name) form.setError(name, { message })
      }
    }
  })

  const hint = (name: keyof FormInput, found: Found<unknown> | null | undefined, fallback?: string) => {
    if (errors[name]) return { error: true, helperText: errors[name]?.message }
    const confidence = confidenceHint(found, reviewed || !reading)
    return {
      error: false,
      helperText: confidence?.text ?? fallback,
      slotProps: { formHelperText: { sx: { color: confidence?.warn ? 'warning.main' : undefined } } },
    }
  }

  const apiFailed = review.isError && !(review.error instanceof ApiError && review.error.problem.errors)

  return (
    <Stack spacing={2}>
      <Card variant="outlined">
        <CardContent>
          <form onSubmit={submit} noValidate aria-label="Έλεγχος παραστατικού">
            <Stack spacing={2}>
              <Typography variant="h6" component="h2">
                {reviewed ? 'Επιβεβαιωμένα στοιχεία' : reading ? 'Ελέγξτε τι διαβάστηκε' : 'Συμπληρώστε τα στοιχεία'}
              </Typography>
              {review.isSuccess && !form.formState.isDirty && (
                <Alert severity="success">
                  Αποθηκεύτηκε.
                  {review.data.review && review.data.review.correctedFields.length > 0 &&
                    ` Οι διορθώσεις σας (${review.data.review.correctedFields.length}) θα βοηθήσουν να βελτιωθεί η ανάγνωση.`}
                </Alert>
              )}
              {apiFailed && <Alert severity="error">Η αποθήκευση δεν ολοκληρώθηκε. Δοκιμάστε ξανά.</Alert>}
              <TextField label="ΑΦΜ εκδότη" inputMode="numeric" {...form.register('issuerAfm')} {...hint('issuerAfm', reading?.issuerAfm)} />
              <TextField
                label="Ημερομηνία έκδοσης"
                type="date"
                {...form.register('issueDate')}
                {...hint('issueDate', reading?.issueDate)}
                slotProps={{ inputLabel: { shrink: true }, ...hint('issueDate', reading?.issueDate).slotProps }}
              />
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                <TextField label="Καθαρό" inputMode="decimal" fullWidth {...form.register('netAmount')} {...hint('netAmount', reading?.netAmount)} />
                <TextField label="ΦΠΑ" inputMode="decimal" fullWidth {...form.register('vatAmount')} {...hint('vatAmount', reading?.vatAmount)} />
                <TextField label="Σύνολο" inputMode="decimal" fullWidth {...form.register('totalAmount')} {...hint('totalAmount', reading?.totalAmount)} />
              </Stack>
              <Controller
                control={form.control}
                name="category"
                render={({ field }) => (
                  <TextField
                    select
                    label="Κατηγορία εξόδου"
                    {...field}
                    error={!!errors.category}
                    helperText={
                      errors.category?.message ??
                      (reading && !reviewed
                        ? `Πρόταση μοντέλου: βεβαιότητα ${Math.round(reading.category.confidence * 100)}%`
                        : undefined)
                    }
                  >
                    {Object.entries(categoryLabels).map(([code, label]) => (
                      <MenuItem key={code} value={code}>
                        {label}
                      </MenuItem>
                    ))}
                  </TextField>
                )}
              />
              <Stack direction="row" sx={{ justifyContent: 'flex-end' }}>
                <Button type="submit" variant="contained" disabled={isSubmitting}>
                  {reviewed ? 'Αποθήκευση αλλαγών' : 'Επιβεβαίωση'}
                </Button>
              </Stack>
            </Stack>
          </form>
        </CardContent>
      </Card>

      {reading && reading.checks.length > 0 && (
        <Card variant="outlined">
          <CardContent>
            <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 600 }}>
              Έλεγχοι
            </Typography>
            <List dense aria-label="Έλεγχοι">
              {reading.checks.map(check => (
                <ListItem key={check.name} disableGutters>
                  <ListItemIcon sx={{ minWidth: 32 }}>
                    {check.passed ? (
                      <CheckCircleRoundedIcon fontSize="small" color="success" titleAccess="Πέρασε" />
                    ) : (
                      <ErrorRoundedIcon fontSize="small" color="error" titleAccess="Απέτυχε" />
                    )}
                  </ListItemIcon>
                  <ListItemText primary={checkLabels[check.name] ?? check.name} secondary={check.detail} />
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      )}
    </Stack>
  )
}
