import { Alert, Button, Card, CardContent, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { useState } from 'react'
import { ApiError } from '../../api/http'
import { hasRole, useSession } from '../../auth/session'
import { formatMoney } from '../documents/files'
import { useDecide, useDisburse, useRecommend, useStartReview, type Case, type Outcome } from './api'

const failure = (error: unknown) =>
  error instanceof ApiError && error.problem.detail ? error.problem.detail : 'Η ενέργεια δεν ολοκληρώθηκε. Δοκιμάστε ξανά.'

/** Approve or decline, with the reasons in words: an analyst's recommendation or a manager's decision. */
function VerdictForm({ title, action, pending, onSend }: {
  title: string
  action: string
  pending: boolean
  onSend: (outcome: Outcome, note: string) => void
}) {
  const [outcome, setOutcome] = useState<Outcome>('APPROVE')
  const [note, setNote] = useState('')
  return (
    <Stack spacing={1.5}>
      <Typography variant="h6" component="h2">
        {title}
      </Typography>
      <ToggleButtonGroup exclusive value={outcome} onChange={(_, next: Outcome | null) => next && setOutcome(next)} aria-label={title}>
        <ToggleButton value="APPROVE">Έγκριση</ToggleButton>
        <ToggleButton value="DECLINE">Απόρριψη</ToggleButton>
      </ToggleButtonGroup>
      <TextField label="Αιτιολόγηση" multiline minRows={3} value={note} onChange={e => setNote(e.target.value)} slotProps={{ htmlInput: { maxLength: 1000 } }} />
      <Button variant="contained" disabled={pending || !note.trim()} onClick={() => onSend(outcome, note.trim())} sx={{ alignSelf: 'flex-start' }}>
        {action}
      </Button>
    </Stack>
  )
}

/** The next step the signed-in person may take on the case, if any. */
export function ReviewActions({ loan }: { loan: Case }) {
  const me = useSession().data
  const startReview = useStartReview(loan.id)
  const recommend = useRecommend(loan.id)
  const decide = useDecide(loan.id)
  const disburse = useDisburse(loan.id)
  if (!me) return null

  const analyst = hasRole(me, 'ANALYST')
  const decider = hasRole(me, 'MANAGER') || hasRole(me, 'ADMIN')
  const error = [startReview, recommend, decide, disburse].find(m => m.isError)?.error

  let content = null
  if (analyst && loan.status === 'SUBMITTED') {
    content = (
      <Button variant="contained" disabled={startReview.isPending} onClick={() => startReview.mutate()}>
        Ανάληψη αξιολόγησης
      </Button>
    )
  } else if (analyst && loan.status === 'IN_REVIEW') {
    content = (
      <VerdictForm title="Εισήγηση" action="Υποβολή εισήγησης" pending={recommend.isPending} onSend={(outcome, note) => recommend.mutate({ outcome, note })} />
    )
  } else if (decider && loan.status === 'RECOMMENDED') {
    content = (
      <VerdictForm title="Απόφαση" action="Οριστική απόφαση" pending={decide.isPending} onSend={(outcome, note) => decide.mutate({ outcome, note })} />
    )
  } else if (decider && loan.status === 'APPROVED') {
    content = (
      <Button variant="contained" disabled={disburse.isPending} onClick={() => disburse.mutate()}>
        Εκταμίευση {formatMoney(loan.amount)}
      </Button>
    )
  }
  if (!content) return null

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2}>
          {error && <Alert severity="error">{failure(error)}</Alert>}
          {content}
        </Stack>
      </CardContent>
    </Card>
  )
}
