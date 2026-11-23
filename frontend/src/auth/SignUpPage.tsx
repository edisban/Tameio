import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Link, Stack, TextField, Typography } from '@mui/material'
import { useForm } from 'react-hook-form'
import { Navigate, Link as RouterLink, useNavigate } from 'react-router'
import { z } from 'zod'
import { ApiError } from '../api/http'
import { AuthLayout } from './AuthLayout'
import { PasswordField } from './PasswordField'
import { useSession, useSignUp } from './session'

// The same rules as the API
const schema = z.object({
  fullName: z.string().trim().min(1, 'Γράψτε το ονοματεπώνυμό σας').max(200, 'Έως 200 χαρακτήρες'),
  email: z.email('Μη έγκυρο email'),
  password: z.string().min(8, 'Τουλάχιστον 8 χαρακτήρες').max(128, 'Έως 128 χαρακτήρες'),
})

type Form = z.infer<typeof schema>

export function SignUpPage() {
  const session = useSession()
  const signUp = useSignUp()
  const navigate = useNavigate()
  const form = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { fullName: '', email: '', password: '' } })
  const { errors, isSubmitting } = form.formState

  const submit = form.handleSubmit(async values => {
    try {
      await signUp.mutateAsync(values)
      navigate('/businesses', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.problem.status === 409) {
        form.setError('email', { message: 'Υπάρχει ήδη λογαριασμός με αυτό το email' })
      }
    }
  })

  const unexpected = signUp.isError && !(signUp.error instanceof ApiError && signUp.error.problem.status === 409)

  if (session.data) return <Navigate to="/businesses" replace />

  return (
    <AuthLayout title="Εγγραφή" subtitle="Δημιουργήστε λογαριασμό για την επιχείρησή σας.">
      <form onSubmit={submit} noValidate>
        <Stack spacing={2}>
          {unexpected && <Alert severity="error">Η εγγραφή δεν ολοκληρώθηκε. Δοκιμάστε ξανά.</Alert>}
          <TextField
            label="Ονοματεπώνυμο"
            autoComplete="name"
            autoFocus
            {...form.register('fullName')}
            error={!!errors.fullName}
            helperText={errors.fullName?.message}
          />
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            {...form.register('email')}
            error={!!errors.email}
            helperText={errors.email?.message}
          />
          <PasswordField
            label="Κωδικός"
            autoComplete="new-password"
            {...form.register('password')}
            error={!!errors.password}
            helperText={errors.password?.message ?? 'Τουλάχιστον 8 χαρακτήρες'}
          />
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Δημιουργία λογαριασμού
          </Button>
          <Typography color="text.secondary" sx={{ textAlign: 'center' }}>
            Έχετε ήδη λογαριασμό;{' '}
            <Link component={RouterLink} to="/sign-in">
              Σύνδεση
            </Link>
          </Typography>
        </Stack>
      </form>
    </AuthLayout>
  )
}
