import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Stack, TextField } from '@mui/material'
import { useForm } from 'react-hook-form'
import { Navigate, useNavigate } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/http'
import { AuthLayout } from './AuthLayout'
import { homeOf, useChangePassword, useSession } from './session'

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Γράψτε τον τωρινό κωδικό'),
    newPassword: z.string().min(12, 'Τουλάχιστον 12 χαρακτήρες').max(128, 'Έως 128 χαρακτήρες'),
    repeat: z.string(),
  })
  .refine(form => form.newPassword === form.repeat, { path: ['repeat'], message: 'Οι δύο κωδικοί διαφέρουν' })
  .refine(form => form.newPassword !== form.currentPassword, { path: ['newPassword'], message: 'Διαλέξτε κωδικό διαφορετικό από τον τωρινό' })

type Form = z.infer<typeof schema>

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.problem.status === 401) return 'Ο τωρινός κωδικός είναι λάθος.'
  return 'Η αλλαγή δεν ολοκληρώθηκε. Δοκιμάστε ξανά.'
}

/**
 * Choosing a new password. Bank staff land here at first sign-in with the temporary password they were
 * given; until they choose their own, the API refuses everything else.
 */
export function ChangePasswordPage() {
  const session = useSession()
  const change = useChangePassword()
  const navigate = useNavigate()
  const form = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { currentPassword: '', newPassword: '', repeat: '' } })
  const { errors, isSubmitting } = form.formState

  const submit = form.handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      const me = await change.mutateAsync({ currentPassword, newPassword })
      navigate(homeOf(me), { replace: true })
    } catch {
      // Shown from change.error below
    }
  })

  if (session.isPending) return null
  if (!session.data) return <Navigate to="/sign-in" replace />

  return (
    <AuthLayout
      title="Νέος κωδικός"
      subtitle={
        session.data.mustChangePassword
          ? 'Συνδεθήκατε με προσωρινό κωδικό. Διαλέξτε δικό σας για να συνεχίσετε.'
          : 'Διαλέξτε νέο κωδικό. Θα αποσυνδεθείτε από τις άλλες συσκευές σας.'
      }
    >
      <form onSubmit={submit} noValidate>
        <Stack spacing={2.5}>
          {change.isError && <Alert severity="error">{errorMessage(change.error)}</Alert>}
          <TextField
            label="Τωρινός κωδικός"
            type="password"
            autoComplete="current-password"
            autoFocus
            {...form.register('currentPassword')}
            error={!!errors.currentPassword}
            helperText={errors.currentPassword?.message}
          />
          <TextField
            label="Νέος κωδικός"
            type="password"
            autoComplete="new-password"
            {...form.register('newPassword')}
            error={!!errors.newPassword}
            helperText={errors.newPassword?.message ?? 'Τουλάχιστον 12 χαρακτήρες. Μια φράση είναι καλή επιλογή.'}
          />
          <TextField
            label="Νέος κωδικός ξανά"
            type="password"
            autoComplete="new-password"
            {...form.register('repeat')}
            error={!!errors.repeat}
            helperText={errors.repeat?.message}
          />
          <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
            Αλλαγή κωδικού
          </Button>
        </Stack>
      </form>
    </AuthLayout>
  )
}
