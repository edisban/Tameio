import CallMadeRoundedIcon from '@mui/icons-material/CallMadeRounded'
import CallReceivedRoundedIcon from '@mui/icons-material/CallReceivedRounded'
import { Alert, Avatar, Box, Card, List, ListItem, ListItemAvatar, ListItemText, Skeleton, Stack, TablePagination, Typography } from '@mui/material'
import { formatDateTime, formatMoney } from '../documents/files'
import type { Page } from '../../api/http'
import type { Transaction } from './api'

const signed = (amount: number) => (amount > 0 ? `+${formatMoney(amount)}` : `−${formatMoney(-amount)}`)

/**
 * The statement: newest first, money in and out told apart by an arrow and the sign as well as colour,
 * with the balance after each movement.
 */
export function TransactionsList({ data, isPending, isError, onPage }: {
  data: Page<Transaction> | undefined
  isPending: boolean
  isError: boolean
  onPage: (page: number) => void
}) {
  if (isError) return <Alert severity="error">Δεν ήταν δυνατό να φορτωθούν οι κινήσεις.</Alert>
  if (isPending || !data) return <Skeleton variant="rounded" height={240} />

  if (data.page.totalElements === 0) {
    return (
      <Card variant="outlined">
        <Box sx={{ textAlign: 'center', py: 5, px: 2 }}>
          <Typography variant="h6">Καμία κίνηση ακόμα</Typography>
          <Typography color="text.secondary">Οι πληρωμές που στέλνετε και λαμβάνετε θα εμφανίζονται εδώ.</Typography>
        </Box>
      </Card>
    )
  }

  return (
    <Card variant="outlined">
      <List aria-label="Κινήσεις λογαριασμού" disablePadding>
        {data.content.map((t, i) => {
          const incoming = t.amount > 0
          return (
            <ListItem key={t.id} divider={i < data.content.length - 1} sx={{ py: 1.5 }}>
              <ListItemAvatar>
                <Avatar sx={{ bgcolor: incoming ? 'success.light' : 'primary.light', color: incoming ? 'success.main' : 'primary.main' }}>
                  {incoming ? <CallReceivedRoundedIcon titleAccess="Εισερχόμενη" /> : <CallMadeRoundedIcon titleAccess="Εξερχόμενη" />}
                </Avatar>
              </ListItemAvatar>
              <ListItemText
                primary={t.counterpartyName ?? t.description}
                secondary={[formatDateTime(t.bookedAt), t.reference].filter(Boolean).join(' · ')}
                slotProps={{ primary: { sx: { fontWeight: 600 } } }}
              />
              <Stack sx={{ alignItems: 'flex-end', ml: 2 }}>
                <Typography sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap', color: incoming ? 'success.main' : 'text.primary' }}>
                  {signed(t.amount)}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
                  Υπόλοιπο {formatMoney(t.balanceAfter)}
                </Typography>
              </Stack>
            </ListItem>
          )
        })}
      </List>
      <TablePagination
        component="div"
        count={data.page.totalElements}
        page={data.page.number}
        rowsPerPage={data.page.size}
        rowsPerPageOptions={[]}
        onPageChange={(_, next) => onPage(next)}
        labelDisplayedRows={({ from, to, count }) => `${from}–${to} από ${count}`}
      />
    </Card>
  )
}
