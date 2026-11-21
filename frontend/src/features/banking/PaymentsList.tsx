import { Card, Chip, List, ListItem, ListItemText, Stack, Tooltip, Typography } from '@mui/material'
import { formatDateTime, formatMoney } from '../documents/files'
import { usePayments, type PaymentStatus } from './api'

const statusLabel: Record<PaymentStatus, string> = {
  COMPLETED: 'Ολοκληρώθηκε',
  PENDING: 'Σε εκκαθάριση',
  SETTLED: 'Εκκαθαρίστηκε',
  RETURNED: 'Επιστράφηκε',
}

const statusColour: Record<PaymentStatus, { bgcolor: string; color: string }> = {
  COMPLETED: { bgcolor: 'success.light', color: 'success.main' },
  PENDING: { bgcolor: 'primary.light', color: 'primary.main' },
  SETTLED: { bgcolor: 'success.light', color: 'success.main' },
  RETURNED: { bgcolor: 'error.light', color: 'error.main' },
}

/** ISO 20022 reason codes the clearing house sends back, in words. */
const reasons: Record<string, string> = {
  AC01: 'Λάθος αριθμός λογαριασμού',
  AC04: 'Ο λογαριασμός έχει κλείσει',
}

/** Recent payments with where each stands; a payment in clearing updates by itself. */
export function PaymentsList({ businessId, accountId }: { businessId: string; accountId: string }) {
  const payments = usePayments(businessId, accountId)
  const list = payments.data?.content ?? []
  if (list.length === 0) return null

  return (
    <Stack spacing={1.5}>
      <Typography variant="h6" component="h2">
        Πληρωμές
      </Typography>
      <Card variant="outlined">
        <List aria-label="Πληρωμές" disablePadding>
          {list.map((p, i) => (
            <ListItem key={p.id} divider={i < list.length - 1}>
              <ListItemText
                primary={`${p.direction === 'INCOMING' ? 'Από' : 'Προς'} ${p.counterpartyName}`}
                secondary={[formatDateTime(p.createdAt), p.scheme === 'SEPA' ? 'SEPA' : 'Tameio', p.reference].filter(Boolean).join(' · ')}
                slotProps={{ primary: { sx: { fontWeight: 600 } } }}
              />
              <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', ml: 2 }}>
                <Typography sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
                  {p.direction === 'INCOMING' ? '+' : '−'}
                  {formatMoney(p.amount)}
                </Typography>
                <Tooltip title={p.reasonCode ? `${p.reasonCode}: ${reasons[p.reasonCode] ?? p.reason}` : ''}>
                  <Chip size="small" label={statusLabel[p.status]} sx={statusColour[p.status]} />
                </Tooltip>
              </Stack>
            </ListItem>
          ))}
        </List>
      </Card>
    </Stack>
  )
}
