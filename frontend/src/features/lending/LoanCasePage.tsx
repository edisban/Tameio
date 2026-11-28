import { Alert, Box, Breadcrumbs, Card, CardContent, Chip, Link, Skeleton, Stack, Typography } from '@mui/material'
import { Link as RouterLink, useParams } from 'react-router'
import { formatDateTime, formatMoney, formatRate } from '../../format'
import { useCase, type Case } from './api'
import { purposeLabel, statusColour, statusLabel } from './labels'
import { ReviewActions } from './ReviewActions'
import { CreditPanel } from './CreditPanel'
import { RiskPanel } from './RiskPanel'

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 2 }}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography sx={{ fontVariantNumeric: 'tabular-nums', textAlign: 'right' }}>{value}</Typography>
    </Stack>
  )
}

/** The application's terms and the figures it was assessed on. */
function Terms({ loan }: { loan: Case }) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={1}>
          <Typography variant="h6" component="h2">
            Αίτηση
          </Typography>
          <Row label="Ποσό" value={formatMoney(loan.amount)} />
          <Row label="Διάρκεια" value={`${loan.termMonths} μήνες`} />
          <Row label="Δόση" value={`${formatMoney(loan.monthlyInstalment)} με ${formatRate(loan.annualRate)}`} />
          <Row label="Σκοπός" value={purposeLabel[loan.purpose]} />
          {loan.description && <Typography color="text.secondary">«{loan.description}»</Typography>}
          <Typography variant="h6" component="h2" sx={{ pt: 1 }}>
            Στοιχεία κατά την υποβολή
          </Typography>
          <Row label="Μέσες μηνιαίες εισροές" value={formatMoney(loan.figures.averageMonthlyInflow)} />
          <Row label="Μέσες μηνιαίες εκροές" value={formatMoney(loan.figures.averageMonthlyOutflow)} />
          <Row label="Μέσα μηνιαία έξοδα (λογιστικά)" value={formatMoney(loan.figures.averageMonthlyExpenses)} />
          <Row label="Υπόλοιπο λογαριασμού" value={formatMoney(loan.figures.balance)} />
          <Row label="Ιστορικό" value={`${loan.figures.monthsOfHistory} μήνες`} />
        </Stack>
      </CardContent>
    </Card>
  )
}

/** Who did each step of the review, and what they wrote. */
function History({ loan }: { loan: Case }) {
  const steps = [
    { label: 'Υποβολή', at: loan.submittedAt, by: loan.businessName, note: null },
    loan.review && { label: 'Ανάληψη αξιολόγησης', at: loan.review.at, by: loan.review.name, note: null },
    loan.recommended && {
      label: `Εισήγηση: ${loan.recommendation === 'APPROVE' ? 'έγκριση' : 'απόρριψη'}`,
      at: loan.recommended.at,
      by: loan.recommended.name,
      note: loan.recommendationNote,
    },
    loan.decided && { label: `Απόφαση: ${statusLabel[loan.status === 'DISBURSED' ? 'APPROVED' : loan.status]}`, at: loan.decided.at, by: loan.decided.name, note: loan.decisionNote },
    loan.disbursedAt && { label: 'Εκταμίευση', at: loan.disbursedAt, by: null, note: null },
  ].filter(step => !!step)

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
          Ιστορικό
        </Typography>
        <Stack component="ol" spacing={1.5} aria-label="Ιστορικό αίτησης" sx={{ listStyle: 'none', p: 0, m: 0 }}>
          {steps.map(step => (
            <Box component="li" key={step.label}>
              <Typography sx={{ fontWeight: 600 }}>{step.label}</Typography>
              <Typography variant="body2" color="text.secondary">
                {[step.by, formatDateTime(step.at)].filter(Boolean).join(' · ')}
              </Typography>
              {step.note && <Typography variant="body2">«{step.note}»</Typography>}
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  )
}

/** One loan case in the back office: terms and figures, the risk score with its factors, the history, the next step. */
export function LoanCasePage() {
  const { id = '' } = useParams()
  const loan = useCase(id)

  return (
    <Stack spacing={3}>
      <Breadcrumbs aria-label="Θέση στην εφαρμογή">
        <Link component={RouterLink} to="/bank" underline="hover" color="text.secondary">
          Αιτήσεις δανείων
        </Link>
        <Typography color="text.primary">{loan.data?.businessName ?? '…'}</Typography>
      </Breadcrumbs>
      {loan.isError && <Alert severity="error">Η αίτηση δεν βρέθηκε.</Alert>}
      {loan.isPending && <Skeleton variant="rounded" height={360} />}
      {loan.data && (
        <>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
            <Typography variant="h4" component="h1">
              {loan.data.businessName}
            </Typography>
            <Chip label={statusLabel[loan.data.status]} sx={statusColour[loan.data.status]} />
          </Stack>
          <ReviewActions loan={loan.data} />
          <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, alignItems: 'start' }}>
            <Stack spacing={3}>
              <Terms loan={loan.data} />
              <History loan={loan.data} />
            </Stack>
            <Stack spacing={3}>
              <RiskPanel loan={loan.data} />
              {loan.data.credit && <CreditPanel credit={loan.data.credit} />}
            </Stack>
          </Box>
        </>
      )}
    </Stack>
  )
}
