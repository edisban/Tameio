import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from '@mui/material'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../../api/http'
import { isValidAfm } from './afm'
import { useRegisterBusiness } from './api'

const schema = z.object({
  name: z.string().trim().min(1, 'Γράψτε την επωνυμία').max(200, 'Έως 200 χαρακτήρες'),
  afm: z.string().refine(isValidAfm, 'Μη έγκυρο ΑΦΜ'),
})

type Form = z.infer<typeof schema>

export function RegisterBusinessDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const register = useRegisterBusiness()
  const form = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { name: '', afm: '' } })
  const { errors, isSubmitting } = form.formState

  const close = () => {
    form.reset()
    register.reset()
    onClose()
  }

  const submit = form.handleSubmit(async values => {
    try {
      await register.mutateAsync(values)
      close()
    } catch (e) {
      if (!(e instanceof ApiError)) return
      // A taken ΑΦΜ is about that field, so it is shown under it
      if (e.problem.status === 409) {
        form.setError('afm', { message: 'Υπάρχει ήδη επιχείρηση με αυτό το ΑΦΜ' })
        return
      }
      // Field errors from the API's own validation appear under the field they belong to
      for (const [field, message] of Object.entries(e.problem.errors ?? {})) {
        if (field === 'afm' || field === 'name') form.setError(field, { message })
      }
    }
  })

  // Anything not tied to a field (the server is down, an unexpected answer) is shown above the form
  const handledOnField = register.error instanceof ApiError && (register.error.problem.status === 409 || !!register.error.problem.errors)
  const apiError = register.error && !handledOnField ? 'Η καταχώριση δεν ολοκληρώθηκε. Δοκιμάστε ξανά.' : null

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="xs">
      <form onSubmit={submit} noValidate>
        <DialogTitle>Νέα επιχείρηση</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {apiError && <Alert severity="error">{apiError}</Alert>}
            <TextField label="Επωνυμία" autoFocus {...form.register('name')} error={!!errors.name} helperText={errors.name?.message} />
            <TextField
              label="ΑΦΜ"
              inputMode="numeric"
              {...form.register('afm')}
              error={!!errors.afm}
              helperText={errors.afm?.message ?? '9 ψηφία'}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={close}>Άκυρο</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Καταχώριση
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
