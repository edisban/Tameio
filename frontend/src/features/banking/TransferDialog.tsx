import { zodResolver } from '@hookform/resolvers/zod'
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import ScheduleRoundedIcon from '@mui/icons-material/ScheduleRounded'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../../api/http'
import { parseCents } from '../documents/amounts'
import { formatMoney } from '../documents/files'
import { useDirectory, useTransfer, useVerifyPayee, type BankAccount, type Payment, type PayeeCheck } from './api'
import { formatIban, isValidIban, normaliseIban } from './iban'

const schema = z.object({
  creditorIban: z.string().refine(isValidIban, 'Ελέγξτε το IBAN: δεν είναι έγκυρο'),
  creditorName: z.string().trim().min(1, 'Γράψτε το όνομα του δικαιούχου').max(140),
  amount: z.string().refine(v => (parseCents(v) ?? 0) > 0, 'Γράψτε ποσό σε ευρώ, π.χ. 120,50'),
  reference: z.string().max(140, 'Έως 140 χαρακτήρες'),
})

type Form = z.infer<typeof schema>

const TAMEIO_BANK_CODE = '999'
const isTameio = (iban: string) => {
  const n = normaliseIban(iban)
  return n.startsWith('GR') && n.slice(4, 7) === TAMEIO_BANK_CODE
}

const failure = (error: unknown) =>
  error instanceof ApiError && error.problem.status === 422
    ? (error.problem.detail ?? 'Η πληρωμή δεν μπορεί να γίνει.')
    : 'Η πληρωμή δεν ολοκληρώθηκε. Δοκιμάστε ξανά: δεν θα χρεωθείτε δύο φορές.'

/** What Verification of Payee found, in words; a mismatch asks for an explicit confirmation. */
function PayeeCheckNotice({ check, typed, onUseName, confirmed, onConfirm }: {
  check: PayeeCheck | null
  typed: string
  onUseName: (name: string) => void
  confirmed: boolean
  onConfirm: (value: boolean) => void
}) {
  if (!check) return null
  switch (check.result) {
    case 'MATCH':
      return <Alert severity="success">Το όνομα ταιριάζει με τον κάτοχο του λογαριασμού.</Alert>
    case 'CLOSE_MATCH':
      return (
        <Alert
          severity="warning"
          action={<Button color="inherit" size="small" onClick={() => onUseName(check.actualName!)}>Χρήση</Button>}
        >
          Μοιάζει: ο λογαριασμός ανήκει σε «{check.actualName}», όχι «{typed}».
        </Alert>
      )
    case 'NO_MATCH':
      return (
        <Alert severity="error">
          Το όνομα δεν ταιριάζει με τον κάτοχο του λογαριασμού. Αν πληρώσετε, τα χρήματα μπορεί να πάνε σε λάθος πρόσωπο και να μην επιστραφούν.
          <FormControlLabel
            sx={{ display: 'flex', mt: 1 }}
            control={<Checkbox checked={confirmed} onChange={e => onConfirm(e.target.checked)} />}
            label="Το κατανοώ και θέλω να πληρώσω"
          />
        </Alert>
      )
    default:
      return <Alert severity="info">Η τράπεζα του δικαιούχου δεν επιβεβαίωσε το όνομα. Ελέγξτε το IBAN πριν πληρώσετε.</Alert>
  }
}

/**
 * A payment in three steps: details, a review with the payee check (Verification of Payee), and the
 * receipt. The Idempotency-Key is made when the review opens and kept for retries, so pressing "Pay"
 * twice, or again after a lost answer, pays once.
 */
export function TransferDialog({ businessId, account, open, onClose }: {
  businessId: string
  account: BankAccount
  open: boolean
  onClose: () => void
}) {
  const transfer = useTransfer(businessId, account.id)
  const verify = useVerifyPayee()
  const directory = useDirectory()
  const [step, setStep] = useState<'details' | 'review' | 'done'>('details')
  const [key, setKey] = useState('')
  const [check, setCheck] = useState<PayeeCheck | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const [result, setResult] = useState<Payment | null>(null)
  const form = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { creditorIban: '', creditorName: '', amount: '', reference: '' } })
  const { errors } = form.formState
  const values = form.getValues()
  const cents = parseCents(values.amount) ?? 0
  const sepa = !isTameio(values.creditorIban)

  const close = () => {
    form.reset()
    transfer.reset()
    setStep('details')
    setCheck(null)
    setConfirmed(false)
    setResult(null)
    onClose()
  }

  const review = form.handleSubmit(async ({ creditorIban, creditorName }) => {
    setKey(crypto.randomUUID())
    setConfirmed(false)
    try {
      setCheck(await verify.mutateAsync({ iban: normaliseIban(creditorIban), name: creditorName.trim() }))
    } catch {
      setCheck({ result: 'NOT_POSSIBLE', actualName: null })
    }
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
        confirmedMismatch: check?.result === 'NO_MATCH' ? confirmed : undefined,
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
              {directory.data && directory.data.length > 0 && (
                <TextField
                  select
                  size="small"
                  label="Δικαιούχοι demo σε άλλες τράπεζες"
                  value=""
                  onChange={e => {
                    const payee = directory.data.find(p => p.iban === e.target.value)
                    if (!payee) return
                    form.setValue('creditorIban', formatIban(payee.iban), { shouldValidate: true })
                    form.setValue('creditorName', payee.holderName, { shouldValidate: true })
                  }}
                >
                  {directory.data.map(p => (
                    <MenuItem key={p.iban} value={p.iban}>
                      {p.holderName} · {p.bank}
                    </MenuItem>
                  ))}
                </TextField>
              )}
              <TextField
                label="IBAN δικαιούχου"
                autoFocus
                {...form.register('creditorIban', { onChange: e => form.setValue('creditorIban', formatIban(e.target.value)) })}
                error={!!errors.creditorIban}
                helperText={errors.creditorIban?.message ?? 'Σε λογαριασμό Tameio ή σε άλλη τράπεζα της ζώνης SEPA'}
                slotProps={{ htmlInput: { style: { fontFamily: 'monospace' }, maxLength: 42 }, inputLabel: { shrink: true } }}
              />
              <TextField
                label="Όνομα δικαιούχου"
                {...form.register('creditorName')}
                error={!!errors.creditorName}
                helperText={errors.creditorName?.message ?? 'Όπως είναι στον λογαριασμό: θα το ελέγξουμε'}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField label="Ποσό (€)" inputMode="decimal" {...form.register('amount')} error={!!errors.amount} helperText={errors.amount?.message} />
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
            <Button type="submit" variant="contained" disabled={verify.isPending}>
              Συνέχεια
            </Button>
          </DialogActions>
        </form>
      )}

      {step === 'review' && (
        <>
          <DialogTitle>Επιβεβαίωση πληρωμής</DialogTitle>
          <DialogContent>
            <Stack spacing={1.5} sx={{ pt: 1 }}>
              {transfer.isError && <Alert severity="error">{failure(transfer.error)}</Alert>}
              <PayeeCheckNotice
                check={check}
                typed={values.creditorName.trim()}
                confirmed={confirmed}
                onConfirm={setConfirmed}
                onUseName={name => {
                  form.setValue('creditorName', name)
                  setCheck({ result: 'MATCH', actualName: null })
                }}
              />
              <Typography sx={{ fontSize: 34, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{formatMoney(cents / 100)}</Typography>
              <Divider />
              <Row label="Προς" value={form.getValues('creditorName')} />
              <Row label="IBAN" value={formatIban(values.creditorIban)} mono />
              {values.reference && <Row label="Αιτιολογία" value={values.reference} />}
              <Row label="Από" value={`${account.name} · ${formatIban(account.iban)}`} />
              <Row label="Τρόπος" value={sepa ? 'SEPA, προς άλλη τράπεζα: εκκαθαρίζεται μέσα στην ημέρα' : 'Μεταφορά μέσα στο Tameio: άμεσα'} />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setStep('details')} disabled={transfer.isPending}>
              Πίσω
            </Button>
            <Button
              variant="contained"
              onClick={() => void pay()}
              disabled={transfer.isPending || (check?.result === 'NO_MATCH' && !confirmed)}
            >
              Πληρωμή {formatMoney(cents / 100)}
            </Button>
          </DialogActions>
        </>
      )}

      {step === 'done' && result && (
        <>
          <DialogContent sx={{ textAlign: 'center', pt: 4 }}>
            {result.status === 'PENDING' ? (
              <ScheduleRoundedIcon color="primary" sx={{ fontSize: 56 }} />
            ) : (
              <CheckCircleRoundedIcon color="success" sx={{ fontSize: 56 }} />
            )}
            <Typography variant="h5" component="h2" sx={{ mt: 1 }}>
              {result.status === 'PENDING' ? 'Η πληρωμή στάλθηκε' : 'Η πληρωμή ολοκληρώθηκε'}
            </Typography>
            <Typography color="text.secondary" sx={{ mt: 1 }}>
              {formatMoney(result.amount)} στον/στην {result.counterpartyName}.
              {result.balanceAfter != null && ` Νέο υπόλοιπο ${formatMoney(result.balanceAfter)}.`}
              {result.status === 'PENDING' && ' Θα δείτε εδώ πότε εκκαθαρίστηκε από την τράπεζα του δικαιούχου.'}
            </Typography>
            <Box sx={{ mt: 2, fontFamily: 'monospace', fontSize: 12, color: 'text.secondary' }}>Αναφορά {result.endToEndId}</Box>
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
