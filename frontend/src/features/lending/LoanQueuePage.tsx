import { Alert, Card, Chip, Link, Skeleton, Stack, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, Tabs } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router'
import { PageHeader } from '../../app/PageHeader'
import { useSession } from '../../auth/session'
import { formatDateTime, formatMoney } from '../../format'
import { useQueue, type LoanStatus } from './api'
import { bandColour, bandLabel, purposeLabel, statusColour, statusLabel } from './labels'

const filters: { value: LoanStatus | ''; label: string }[] = [
  { value: '', label: 'Όλες' },
  { value: 'SUBMITTED', label: statusLabel.SUBMITTED },
  { value: 'IN_REVIEW', label: statusLabel.IN_REVIEW },
  { value: 'RECOMMENDED', label: statusLabel.RECOMMENDED },
  { value: 'APPROVED', label: statusLabel.APPROVED },
  { value: 'DECLINED', label: statusLabel.DECLINED },
  { value: 'DISBURSED', label: statusLabel.DISBURSED },
]

const num = { fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' } as const

/** The back office's queue of loan applications, by status. */
export function LoanQueuePage() {
  const me = useSession().data
  const [status, setStatus] = useState<LoanStatus | ''>('')
  const [page, setPage] = useState(0)
  const queue = useQueue(status, page)

  return (
    <Stack spacing={3}>
      <PageHeader title="Αιτήσεις δανείων" subtitle={me ? `Καλώς ήρθατε, ${me.name}.` : undefined} />
      <Tabs
        value={status}
        onChange={(_, next: LoanStatus | '') => {
          setStatus(next)
          setPage(0)
        }}
        variant="scrollable"
        aria-label="Φίλτρο κατάστασης"
        sx={{ borderBottom: 1, borderColor: 'divider' }}
      >
        {filters.map(f => (
          <Tab key={f.value || 'all'} value={f.value} label={f.label} />
        ))}
      </Tabs>
      {queue.isError && <Alert severity="error">Δεν ήταν δυνατό να φορτωθούν οι αιτήσεις.</Alert>}
      {queue.isPending && <Skeleton variant="rounded" height={240} />}
      {queue.data && (
        <Card variant="outlined">
          <TableContainer>
            <Table aria-label="Αιτήσεις δανείων">
              <TableHead>
                <TableRow>
                  <TableCell>Επιχείρηση</TableCell>
                  <TableCell align="right">Ποσό</TableCell>
                  <TableCell>Σκοπός</TableCell>
                  <TableCell>Κίνδυνος</TableCell>
                  <TableCell>Κατάσταση</TableCell>
                  <TableCell>Υποβολή</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {queue.data.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} sx={{ textAlign: 'center', color: 'text.secondary', py: 4 }}>
                      Καμία αίτηση σε αυτή την κατάσταση.
                    </TableCell>
                  </TableRow>
                )}
                {queue.data.content.map(item => (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Link component={RouterLink} to={`/bank/loans/${item.id}`} underline="hover" sx={{ fontWeight: 600 }}>
                        {item.businessName}
                      </Link>
                    </TableCell>
                    <TableCell align="right" sx={num}>
                      {formatMoney(item.amount)} · {item.termMonths} μ.
                    </TableCell>
                    <TableCell>{purposeLabel[item.purpose]}</TableCell>
                    <TableCell>
                      <Chip size="small" label={`${item.riskScore} · ${bandLabel[item.riskBand]}`} sx={bandColour[item.riskBand]} />
                    </TableCell>
                    <TableCell>
                      <Chip size="small" label={statusLabel[item.status]} sx={statusColour[item.status]} />
                    </TableCell>
                    <TableCell sx={num}>{formatDateTime(item.submittedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={queue.data.page.totalElements}
            page={queue.data.page.number}
            rowsPerPage={queue.data.page.size}
            rowsPerPageOptions={[]}
            onPageChange={(_, next) => setPage(next)}
            labelDisplayedRows={({ from, to, count }) => `${from}–${to} από ${count}`}
          />
        </Card>
      )}
    </Stack>
  )
}
