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
      // Field errors from the API (e.g. a duplicate ΑΦΜ) appear under the field they belong to
      if (e instanceof ApiError && e.problem.errors) {
        for (const [field, message] of Object.entries(e.problem.errors)) {
          if (field === 'afm' || field === 'name') form.setError(field, { message })
        }
      }
    }
  })

  const apiError = register.error instanceof ApiError && !register.error.problem.errors ? register.error.message : null

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
