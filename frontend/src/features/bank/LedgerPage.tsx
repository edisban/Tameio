import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import ErrorRoundedIcon from '@mui/icons-material/ErrorRounded'
import { Alert, Box, Card, CardContent, Chip, Skeleton, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { PageHeader } from '../../app/PageHeader'
import { formatMoney } from '../documents/files'
import { internalNames, useTrialBalance } from './backOffice'

const num = { fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' } as const

/**
 * The trial balance: every internal account and the customers' deposits. Double entry keeps their sum
 * at exactly zero; if it ever were not, the page would say so first.
 */
export function LedgerPage() {
  const trial = useTrialBalance()

  return (
    <Stack spacing={3}>
      <PageHeader title="Γενικό καθολικό" subtitle="Το ισοζύγιο της τράπεζας: κάθε εγγραφή έχει χρεώσεις και πιστώσεις που αθροίζουν στο μηδέν." />
      {trial.isError && <Alert severity="error">Δεν ήταν δυνατό να φορτωθεί το ισοζύγιο.</Alert>}
      {trial.isPending && <Skeleton variant="rounded" height={320} />}
      {trial.data && (
        <>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="body2" color="text.secondary">Καταθέσεις πελατών</Typography>
                <Typography variant="h5" component="p" sx={num}>{formatMoney(trial.data.customerDeposits)}</Typography>
                <Typography variant="caption" color="text.secondary">σε {trial.data.customerAccounts} λογαριασμούς</Typography>
              </CardContent>
            </Card>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="body2" color="text.secondary">Εγγραφές ημερολογίου</Typography>
                <Typography variant="h5" component="p" sx={num}>{trial.data.journalEntries.toLocaleString('el-GR')}</Typography>
                <Typography variant="caption" color="text.secondary">αμετάβλητες, μόνο με αντιλογισμό διορθώνονται</Typography>
              </CardContent>
            </Card>
            <Card variant="outlined">
              <CardContent>
                <Typography variant="body2" color="text.secondary">Σύνολο ισοζυγίου</Typography>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  <Typography variant="h5" component="p" sx={num}>{formatMoney(trial.data.total)}</Typography>
                  {trial.data.balanced ? (
                    <Chip size="small" icon={<CheckCircleRoundedIcon />} label="Ισοσκελισμένο" sx={{ bgcolor: 'success.light', color: 'success.main' }} />
                  ) : (
                    <Chip size="small" icon={<ErrorRoundedIcon />} label="Δεν ισοσκελίζεται" sx={{ bgcolor: 'error.light', color: 'error.main' }} />
                  )}
                </Stack>
                <Typography variant="caption" color="text.secondary">πρέπει να είναι πάντα 0,00 €</Typography>
              </CardContent>
            </Card>
          </Box>

          <Card variant="outlined">
            <Table aria-label="Ισοζύγιο">
              <TableHead>
                <TableRow>
                  <TableCell>Λογαριασμός</TableCell>
                  <TableCell>Κωδικός</TableCell>
                  <TableCell align="right">Υπόλοιπο</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {trial.data.internal.map(a => (
                  <TableRow key={a.code}>
                    <TableCell>{internalNames[a.code] ?? a.name}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace' }}>{a.code}</TableCell>
                    <TableCell align="right" sx={num}>{formatMoney(a.balance)}</TableCell>
                  </TableRow>
                ))}
                <TableRow>
                  <TableCell>Καταθέσεις πελατών</TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>CUSTOMERS</TableCell>
                  <TableCell align="right" sx={num}>{formatMoney(trial.data.customerDeposits)}</TableCell>
                </TableRow>
                <TableRow sx={{ '& td': { fontWeight: 700, borderBottom: 0 } }}>
                  <TableCell colSpan={2}>Σύνολο</TableCell>
                  <TableCell align="right" sx={num}>{formatMoney(trial.data.total)}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Card>
          <Typography variant="body2" color="text.secondary">
            Τα υπόλοιπα είναι με πρόσημο από τη σκοπιά της τράπεζας: οι καταθέσεις των πελατών είναι θετικές (όσα τους χρωστά η τράπεζα),
            και ο λογαριασμός διακανονισμού αρνητικός κατά τα χρήματα που μπήκαν στην τράπεζα.
          </Typography>
        </>
      )}
    </Stack>
  )
}
