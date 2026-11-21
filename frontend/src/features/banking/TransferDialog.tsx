import { zodResolver } from '@hookform/resolvers/zod'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Stack, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../../api/http'
import { parseCents } from '../documents/amounts'
import { formatMoney } from '../documents/files'
import { useTransfer, type BankAccount, type TransferResult } from './api'
import { formatIban, isValidIban, normaliseIban } from './iban'

const schema = z.object({
  creditorIban: z.string().refine(isValidIban, 'Ελέγξτε το IBAN: δεν είναι έγκυρο'),
  creditorName: z.string().trim().min(1, 'Γράψτε το όνομα του δικαιούχου').max(140),
  amount: z.string().refine(v => (parseCents(v) ?? 0) > 0, 'Γράψτε ποσό σε ευρώ, π.χ. 120,50'),
  reference: z.string().max(140, 'Έως 140 χαρακτήρες'),
})

type Form = z.infer<typeof schema>

const failure = (error: unknown) =>
  error instanceof ApiError && error.problem.status === 422
    ? (error.problem.detail ?? 'Η πληρωμή δεν μπορεί να γίνει.')
    : 'Η πληρωμή δεν ολοκληρώθηκε. Δοκιμάστε ξανά: δεν θα χρεωθείτε δύο φορές.'

/**
 * A payment in three steps: details, a review of exactly what will be paid, and the receipt. The
 * Idempotency-Key is made when the review opens and kept for retries, so pressing "Pay" twice, or again
 * after a lost answer, pays once.
 */
export function TransferDialog({ businessId, account, open, onClose }: {
  businessId: string
  account: BankAccount
  open: boolean
  onClose: () => void
}) {
  const transfer = useTransfer(businessId, account.id)
  const [step, setStep] = useState<'details' | 'review' | 'done'>('details')
  const [key, setKey] = useState('')
  const [result, setResult] = useState<TransferResult | null>(null)
  const form = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { creditorIban: '', creditorName: '', amount: '', reference: '' } })
  const { errors } = form.formState
  const values = form.getValues()
  const cents = parseCents(values.amount) ?? 0

  const close = () => {
    form.reset()
    transfer.reset()
    setStep('details')
    setResult(null)
    onClose()
  }

  const review = form.handleSubmit(() => {
    setKey(crypto.randomUUID())
    setStep('review')
  })

  async function pay() {
    try {
      const done = await transfer.mutateAsync({
        key,
        creditorIban: normaliseIban(values.creditorIban),
        creditorName: values.creditorName.trim(),
        amount: cents / 100,
        reference: values.reference.trim() || null,
      })
      setResult(done)
      setStep('done')
    } catch {
      // Shown below; the same key is kept, so trying again cannot pay twice
    }
  }

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm">
      {step === 'details' && (
        <form onSubmit={review} noValidate>
          <DialogTitle>Νέα μεταφορά</DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              <Typography variant="body2" color="text.secondary">
                Από: {account.name} · {formatIban(account.iban)} · διαθέσιμα {formatMoney(account.balance)}
              </Typography>
              <TextField
                label="IBAN δικαιούχου"
                autoFocus
                {...form.register('creditorIban', {
                  onChange: e => form.setValue('creditorIban', formatIban(e.target.value)),
                })}
                error={!!errors.creditorIban}
                helperText={errors.creditorIban?.message ?? 'Π.χ. GR16 9990 0010 …'}
                slotProps={{ htmlInput: { style: { fontFamily: 'monospace' }, maxLength: 42 } }}
              />
              <TextField label="Όνομα δικαιούχου" {...form.register('creditorName')} error={!!errors.creditorName} helperText={errors.creditorName?.message} />
              <TextField
                label="Ποσό (€)"
                inputMode="decimal"
                {...form.register('amount')}
                error={!!errors.amount}
                helperText={errors.amount?.message}
              />
              <TextField
                label="Αιτιολογία"
                {...form.register('reference')}
                error={!!errors.reference}
                helperText={errors.reference?.message ?? 'Τη βλέπει και ο δικαιούχος, π.χ. αριθμός τιμολογίου'}
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={close}>Άκυρο</Button>
            <Button type="submit" variant="contained">
              Συνέχεια
            </Button>
          </DialogActions>
        </form>
      )}

      {step === 'review' && (
        <>
          <DialogTitle>Επιβεβαίωση πληρωμής</DialogTitle>
          <DialogContent>
            <Stack spacing={1.5} sx={{ pt: 1 }} aria-label="Σύνοψη πληρωμής">
              {transfer.isError && <Alert severity="error">{failure(transfer.error)}</Alert>}
              <Typography sx={{ fontSize: 34, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{formatMoney(cents / 100)}</Typography>
              <Divider />
              <Row label="Προς" value={values.creditorName} />
              <Row label="IBAN" value={formatIban(values.creditorIban)} mono />
              {values.reference && <Row label="Αιτιολογία" value={values.reference} />}
              <Row label="Από" value={`${account.name} · ${formatIban(account.iban)}`} />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setStep('details')} disabled={transfer.isPending}>
              Πίσω
            </Button>
            <Button variant="contained" onClick={() => void pay()} disabled={transfer.isPending}>
              Πληρωμή {formatMoney(cents / 100)}
            </Button>
          </DialogActions>
        </>
      )}

      {step === 'done' && result && (
        <>
          <DialogContent sx={{ textAlign: 'center', pt: 4 }}>
            <CheckCircleRoundedIcon color="success" sx={{ fontSize: 56 }} />
            <Typography variant="h5" component="h2" sx={{ mt: 1 }}>
              Η πληρωμή ολοκληρώθηκε
            </Typography>
            <Typography color="text.secondary" sx={{ mt: 1 }}>
              {formatMoney(result.amount)} στον/στην {result.creditorName}. Νέο υπόλοιπο {formatMoney(result.balanceAfter)}.
            </Typography>
            <Box sx={{ mt: 2, fontFamily: 'monospace', fontSize: 12, color: 'text.secondary' }}>Αναφορά {result.id}</Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2, justifyContent: 'center' }}>
            <Button variant="contained" onClick={close}>
              Κλείσιμο
            </Button>
          </DialogActions>
        </>
      )}
    </Dialog>
  )
}

function Row({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between' }}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography sx={{ textAlign: 'right', fontFamily: mono ? 'monospace' : undefined, overflowWrap: 'anywhere' }}>{value}</Typography>
    </Stack>
  )
}
