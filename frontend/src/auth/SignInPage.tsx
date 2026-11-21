import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Link, Stack, TextField, Typography } from '@mui/material'
import { useForm } from 'react-hook-form'
import { Navigate, Link as RouterLink, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/http'
import { AuthLayout } from './AuthLayout'
import { homeOf, useSession, useSignIn } from './session'

const schema = z.object({
  email: z.string().trim().min(1, 'Γράψτε το email σας'),
  password: z.string().min(1, 'Γράψτε τον κωδικό σας'),
})

type Form = z.infer<typeof schema>

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.problem.status === 401) return 'Λάθος email ή κωδικός.'
  if (error instanceof ApiError && error.problem.status === 429) {
    return 'Πολλές αποτυχημένες προσπάθειες. Ο λογαριασμός κλείδωσε για 15 λεπτά.'
  }
  return 'Η σύνδεση δεν ολοκληρώθηκε. Δοκιμάστε ξανά.'
}

export function SignInPage() {
  const session = useSession()
  const signIn = useSignIn()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  const form = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } })
  const { errors, isSubmitting } = form.formState

  const submit = form.handleSubmit(async values => {
    try {
      const me = await signIn.mutateAsync(values)
      navigate(me.mustChangePassword ? '/change-password' : (from ?? homeOf(me)), { replace: true })
    } catch {
      // Shown from signIn.error below
    }
  })

  if (session.data) return <Navigate to={session.data.mustChangePassword ? '/change-password' : (from ?? homeOf(session.data))} replace />

  return (
    <AuthLayout title="Σύνδεση" subtitle="Συνδεθείτε για να δείτε τις επιχειρήσεις σας.">
      <form onSubmit={submit} noValidate>
        <Stack spacing={2}>
          {signIn.isError && <Alert severity="error">{errorMessage(signIn.error)}</Alert>}
          <TextField
            label="Email"
            type="email"
            autoComplete="username"
            autoFocus
            {...form.register('email')}
            error={!!errors.email}
            helperText={errors.email?.message}
          />
          <TextField
            label="Κωδικός"
            type="password"
            autoComplete="current-password"
            {...form.register('password')}
            error={!!errors.password}
            helperText={errors.password?.message}
          />
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Σύνδεση
          </Button>
          <Typography color="text.secondary" sx={{ textAlign: 'center' }}>
            Δεν έχετε λογαριασμό;{' '}
            <Link component={RouterLink} to="/sign-up">
              Εγγραφή
            </Link>
          </Typography>
        </Stack>
      </form>
    </AuthLayout>
  )
}
