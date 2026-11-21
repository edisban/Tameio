import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../../api/http'
import type { BankAccount } from '../banking/api'
import { formatIban } from '../banking/iban'
import { parseCents } from '../documents/amounts'
import { useDraftApplication } from './api'
import { purposeLabel } from './labels'

// The product's limits, the same as the API's
const MIN_AMOUNT_CENTS = 100_000
const MAX_AMOUNT_CENTS = 50_000_000
const MIN_TERM = 6
const MAX_TERM = 84

const schema = z.object({
  accountId: z.string().min(1, 'Διαλέξτε λογαριασμό'),
  amount: z.string().refine(v => {
    const cents = parseCents(v)
    return cents !== null && cents >= MIN_AMOUNT_CENTS && cents <= MAX_AMOUNT_CENTS
  }, 'Από 1.000 έως 500.000 €'),
  termMonths: z.string().refine(v => /^\d+$/.test(v.trim()) && Number(v) >= MIN_TERM && Number(v) <= MAX_TERM, 'Από 6 έως 84 μήνες'),
  purpose: z.enum(['WORKING_CAPITAL', 'EQUIPMENT', 'RENOVATION', 'EXPANSION']),
  description: z.string().max(500, 'Έως 500 χαρακτήρες'),
})

type Form = z.infer<typeof schema>

/** A new loan application, saved as a draft the business submits when it is ready. */
export function NewApplicationDialog({ businessId, accounts, open, onClose }: {
  businessId: string
  accounts: BankAccount[]
  open: boolean
  onClose: () => void
}) {
  const draft = useDraftApplication(businessId)
  const form = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { accountId: accounts[0]?.id ?? '', amount: '', termMonths: '36', purpose: 'EQUIPMENT', description: '' },
  })
  const { errors, isSubmitting } = form.formState

  const close = () => {
    form.reset()
    draft.reset()
    onClose()
  }

  const submit = form.handleSubmit(async values => {
    try {
      await draft.mutateAsync({
        accountId: values.accountId,
        amount: parseCents(values.amount)! / 100,
        termMonths: Number(values.termMonths),
        purpose: values.purpose,
        description: values.description.trim() || null,
      })
      close()
    } catch {
      // Shown below
    }
  })

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="sm">
      <form onSubmit={submit} noValidate>
        <DialogTitle>Νέα αίτηση δανείου</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {draft.isError && (
              <Alert severity="error">{draft.error instanceof ApiError ? draft.error.message : 'Η αίτηση δεν αποθηκεύτηκε.'}</Alert>
            )}
            <Controller
              control={form.control}
              name="accountId"
              render={({ field }) => (
                <TextField select label="Λογαριασμός πίστωσης" {...field} error={!!errors.accountId} helperText={errors.accountId?.message}>
                  {accounts.map(a => (
                    <MenuItem key={a.id} value={a.id}>
                      {a.name} · {formatIban(a.iban)}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField label="Ποσό (€)" inputMode="decimal" fullWidth {...form.register('amount')} error={!!errors.amount} helperText={errors.amount?.message} />
              <TextField label="Διάρκεια (μήνες)" inputMode="numeric" fullWidth {...form.register('termMonths')} error={!!errors.termMonths} helperText={errors.termMonths?.message} />
            </Stack>
            <Controller
              control={form.control}
              name="purpose"
              render={({ field }) => (
                <TextField select label="Σκοπός" {...field}>
                  {Object.entries(purposeLabel).map(([value, label]) => (
                    <MenuItem key={value} value={value}>
                      {label}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
            <TextField label="Περιγραφή" multiline minRows={2} {...form.register('description')} error={!!errors.description} helperText={errors.description?.message ?? 'Τι θα κάνετε με τα χρήματα'} />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={close}>Άκυρο</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Αποθήκευση πρόχειρης
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
