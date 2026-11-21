import { Alert, Box, Button, Card, CardContent, Chip, Stack, Step, StepLabel, Stepper, Typography } from '@mui/material'
import { formatDateTime, formatLongDay, formatMoney, formatRate } from '../../format'
import type { Application, Repayment } from './api'
import { journey, purposeLabel, statusColour, statusLabel } from './labels'


/** The next instalment, and a warning when some are due. */
function RepaymentLine({ repayment }: { repayment: Repayment }) {
  return (
    <Stack spacing={1}>
      {repayment.instalmentsDue > 0 && (
        <Alert severity="warning">
          {repayment.instalmentsDue === 1 ? 'Μία δόση είναι πληρωτέα.' : `${repayment.instalmentsDue} δόσεις είναι πληρωτέες.`}
        </Alert>
      )}
      {repayment.nextDueOn && repayment.nextAmount !== null && (
        <Typography variant="body2">
          Επόμενη δόση {formatMoney(repayment.nextAmount)} στις {formatLongDay(repayment.nextDueOn)}.
        </Typography>
      )}
    </Stack>
  )
}

/** Where an application is on its way, as the business follows it. */
function Progress({ application }: { application: Application }) {
  if (application.status === 'DRAFT') return null
  const declined = application.status === 'DECLINED'
  const reached = declined ? journey.indexOf('RECOMMENDED') : journey.indexOf(application.status)
  return (
    <Stepper activeStep={reached + 1} alternativeLabel sx={{ mt: 1 }}>
      {journey.map((step, i) => (
        <Step key={step} completed={i <= reached}>
          <StepLabel error={declined && step === 'APPROVED'}>{declined && step === 'APPROVED' ? statusLabel.DECLINED : statusLabel[step]}</StepLabel>
        </Step>
      ))}
    </Stepper>
  )
}

/** One application: the terms, the quoted instalment, its progress, and the bank's answer once there is one. */
export function ApplicationCard({ application, onSubmit, submitting }: {
  application: Application
  onSubmit: () => void
  submitting: boolean
}) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={1.5}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start', gap: 2 }}>
            <Box>
              <Typography variant="h6" component="h3" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatMoney(application.amount)} · {application.termMonths} μήνες
              </Typography>
              <Typography color="text.secondary">
                {purposeLabel[application.purpose]}
                {application.description && ` · ${application.description}`}
              </Typography>
            </Box>
            <Chip label={statusLabel[application.status]} sx={statusColour[application.status]} />
          </Stack>
          <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums' }}>
            Μηνιαία δόση <b>{formatMoney(application.monthlyInstalment)}</b> με επιτόκιο {formatRate(application.annualRate)}
          </Typography>
          <Progress application={application} />
          {application.decisionNote && (
            <Alert severity={application.status === 'DECLINED' ? 'warning' : 'success'}>
              {application.decisionNote}
            </Alert>
          )}
          {application.status === 'DRAFT' && (
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <Button variant="contained" onClick={onSubmit} disabled={submitting}>
                Υποβολή στην τράπεζα
              </Button>
              <Typography variant="body2" color="text.secondary">
                Η τράπεζα θα τη δει με τα στοιχεία του λογαριασμού και των λογιστικών σας τη στιγμή της υποβολής.
              </Typography>
            </Stack>
          )}
          {application.disbursedAt && (
            <Typography variant="body2" color="text.secondary">
              Πιστώθηκε στον λογαριασμό σας στις {formatDateTime(application.disbursedAt)}.
            </Typography>
          )}
          {application.repayment && <RepaymentLine repayment={application.repayment} />}
        </Stack>
      </CardContent>
    </Card>
  )
}
