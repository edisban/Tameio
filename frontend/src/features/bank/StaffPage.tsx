import { zodResolver } from '@hookform/resolvers/zod'
import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import PersonAddAlt1RoundedIcon from '@mui/icons-material/PersonAddAlt1Rounded'
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  IconButton,
  MenuItem,
  Skeleton,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../../api/http'
import { PageHeader } from '../../app/PageHeader'
import { useSession } from '../../auth/session'
import { parseCents } from '../documents/amounts'
import { formatMoney } from '../documents/files'
import {
  roleLabels,
  useCreateStaff,
  useResetPassword,
  useStaff,
  useUpdateStaff,
  type StaffMember,
  type StaffRole,
  type WithTemporaryPassword,
} from './api'

const schema = z
  .object({
    fullName: z.string().trim().min(1, 'Γράψτε το ονοματεπώνυμο').max(200),
    email: z.string().trim().email('Μη έγκυρο email'),
    role: z.enum(['ANALYST', 'MANAGER', 'ADMIN']),
    approvalLimit: z.string(),
  })
  .refine(form => form.role === 'ANALYST' || form.approvalLimit === '' || parseCents(form.approvalLimit) !== null, {
    path: ['approvalLimit'],
    message: 'Γράψτε ποσό σε ευρώ, π.χ. 50.000',
  })

type Form = z.infer<typeof schema>

const limitOf = (role: StaffRole, text: string) => (role === 'ANALYST' || text === '' ? null : parseCents(text)! / 100)

/** The temporary password, shown once: the server keeps only its hash. */
function TemporaryPasswordDialog({ result, onClose }: { result: WithTemporaryPassword | null; onClose: () => void }) {
  const [copied, setCopied] = useState(false)
  return (
    <Dialog open={!!result} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Προσωρινός κωδικός</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Δώστε τον στον/στην {result?.user.fullName} με ασφαλή τρόπο. Θα ζητηθεί αλλαγή του στην πρώτη σύνδεση. Δεν θα εμφανιστεί ξανά.
        </DialogContentText>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', p: 1.5, borderRadius: 1, bgcolor: 'background.default' }}>
          <Typography component="code" aria-label="Προσωρινός κωδικός" sx={{ fontFamily: 'monospace', fontSize: 18, flexGrow: 1 }}>
            {result?.temporaryPassword}
          </Typography>
          <Tooltip title={copied ? 'Αντιγράφηκε' : 'Αντιγραφή'}>
            <IconButton
              aria-label="Αντιγραφή κωδικού"
              onClick={() => {
                void navigator.clipboard?.writeText(result?.temporaryPassword ?? '')
                setCopied(true)
              }}
            >
              <ContentCopyRoundedIcon />
            </IconButton>
          </Tooltip>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button
          variant="contained"
          onClick={() => {
            setCopied(false)
            onClose()
          }}
        >
          Έγινε
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function CreateStaffDialog({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (r: WithTemporaryPassword) => void }) {
  const create = useCreateStaff()
  const form = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', role: 'ANALYST', approvalLimit: '' },
  })
  const { errors, isSubmitting } = form.formState
  const role = useWatch({ control: form.control, name: 'role' })

  const close = () => {
    form.reset()
    create.reset()
    onClose()
  }

  const submit = form.handleSubmit(async values => {
    try {
      const result = await create.mutateAsync({
        fullName: values.fullName,
        email: values.email,
        role: values.role,
        approvalLimit: limitOf(values.role, values.approvalLimit),
      })
      close()
      onCreated(result)
    } catch (e) {
      if (e instanceof ApiError && e.problem.status === 409) form.setError('email', { message: 'Υπάρχει ήδη λογαριασμός με αυτό το email' })
    }
  })

  return (
    <Dialog open={open} onClose={close} fullWidth maxWidth="xs">
      <form onSubmit={submit} noValidate>
        <DialogTitle>Νέο μέλος προσωπικού</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {create.isError && !(create.error instanceof ApiError && create.error.problem.status === 409) && (
              <Alert severity="error">Η δημιουργία δεν ολοκληρώθηκε.</Alert>
            )}
            <TextField label="Ονοματεπώνυμο" autoFocus {...form.register('fullName')} error={!!errors.fullName} helperText={errors.fullName?.message} />
            <TextField label="Email" type="email" {...form.register('email')} error={!!errors.email} helperText={errors.email?.message} />
            <Controller
              control={form.control}
              name="role"
              render={({ field }) => (
                <TextField select label="Ρόλος" {...field}>
                  {Object.entries(roleLabels).map(([value, label]) => (
                    <MenuItem key={value} value={value}>
                      {label}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
            {role !== 'ANALYST' && (
              <TextField
                label="Όριο έγκρισης (€)"
                inputMode="decimal"
                {...form.register('approvalLimit')}
                error={!!errors.approvalLimit}
                helperText={errors.approvalLimit?.message ?? 'Το μεγαλύτερο δάνειο που μπορεί να εγκρίνει. Κενό: κανένα.'}
              />
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={close}>Άκυρο</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>
            Δημιουργία
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}

function StaffRow({ member, isMe, onReset }: { member: StaffMember; isMe: boolean; onReset: (r: WithTemporaryPassword) => void }) {
  const update = useUpdateStaff()
  const reset = useResetPassword()

  return (
    <TableRow hover>
      <TableCell>
        <Typography sx={{ fontWeight: 600 }}>{member.fullName}</Typography>
        <Typography variant="body2" color="text.secondary">{member.email}</Typography>
      </TableCell>
      <TableCell>{roleLabels[member.role]}</TableCell>
      <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
        {member.approvalLimit != null ? formatMoney(member.approvalLimit) : '—'}
      </TableCell>
      <TableCell>
        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
          {!member.active && <Chip size="small" label="Ανενεργός" sx={{ bgcolor: 'error.light', color: 'error.main' }} />}
          {member.mustChangePassword && <Chip size="small" label="Προσωρινός κωδικός" sx={{ bgcolor: 'warning.light', color: 'warning.main' }} />}
          {member.locked && <Chip size="small" label="Κλειδωμένος" sx={{ bgcolor: 'error.light', color: 'error.main' }} />}
          {member.active && !member.mustChangePassword && !member.locked && (
            <Chip size="small" label="Ενεργός" sx={{ bgcolor: 'success.light', color: 'success.main' }} />
          )}
        </Stack>
      </TableCell>
      <TableCell>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'flex-end' }}>
          <Tooltip title={isMe ? 'Δεν μπορείτε να απενεργοποιήσετε τον εαυτό σας' : member.active ? 'Απενεργοποίηση' : 'Ενεργοποίηση'}>
            <span>
              <Switch
                checked={member.active}
                disabled={isMe || update.isPending}
                slotProps={{ input: { 'aria-label': `Ενεργός λογαριασμός: ${member.fullName}` } }}
                onChange={e => update.mutate({ id: member.id, role: member.role, approvalLimit: member.approvalLimit, active: e.target.checked })}
              />
            </span>
          </Tooltip>
          <Button size="small" disabled={reset.isPending} onClick={() => reset.mutate(member.id, { onSuccess: onReset })}>
            Νέος κωδικός
          </Button>
        </Stack>
      </TableCell>
    </TableRow>
  )
}

/** Bank staff, for administrators: who has access, with which role and approval limit. */
export function StaffPage() {
  const me = useSession().data
  const staff = useStaff()
  const [creating, setCreating] = useState(false)
  const [shown, setShown] = useState<WithTemporaryPassword | null>(null)

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Προσωπικό"
        subtitle="Ποιος έχει πρόσβαση στο back office, με ποιον ρόλο και μέχρι ποιο ποσό εγκρίνει."
        action={
          <Button variant="contained" startIcon={<PersonAddAlt1RoundedIcon />} onClick={() => setCreating(true)}>
            Νέο μέλος
          </Button>
        }
      />
      {staff.isError && <Alert severity="error">Δεν ήταν δυνατό να φορτωθεί το προσωπικό.</Alert>}
      {staff.isPending && <Skeleton variant="rounded" height={240} />}
      {staff.data && (
        <Card variant="outlined">
          <TableContainer>
            <Table aria-label="Προσωπικό">
              <TableHead>
                <TableRow>
                  <TableCell>Όνομα</TableCell>
                  <TableCell>Ρόλος</TableCell>
                  <TableCell align="right">Όριο έγκρισης</TableCell>
                  <TableCell>Κατάσταση</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {staff.data.content.map(member => (
                  <StaffRow key={member.id} member={member} isMe={member.id === me?.id} onReset={setShown} />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Card>
      )}
      <Box>
        <Typography variant="body2" color="text.secondary">
          Οι αναλυτές προετοιμάζουν τις αποφάσεις χωρίς δικαίωμα έγκρισης. Πάνω από το όριο ενός διευθυντή χρειάζεται δεύτερη έγκριση.
        </Typography>
      </Box>
      <CreateStaffDialog open={creating} onClose={() => setCreating(false)} onCreated={setShown} />
      <TemporaryPasswordDialog result={shown} onClose={() => setShown(null)} />
    </Stack>
  )
}
