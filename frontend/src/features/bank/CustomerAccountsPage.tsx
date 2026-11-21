import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import {
  Alert,
  Button,
  Card,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  InputAdornment,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useDeferredValue, useState } from 'react'
import { PageHeader } from '../../app/PageHeader'
import { hasRole, useSession } from '../../auth/session'
import type { BankAccount } from '../banking/api'
import { formatIban } from '../banking/iban'
import { formatMoney } from '../../format'
import { useCustomerAccounts, useFreeze } from './backOffice'

function FreezeDialog({ account, onClose }: { account: BankAccount | null; onClose: () => void }) {
  const freeze = useFreeze()
  const [reason, setReason] = useState('')
  const close = () => {
    setReason('')
    freeze.reset()
    onClose()
  }
  return (
    <Dialog open={!!account} onClose={close} fullWidth maxWidth="xs">
      <DialogTitle>Πάγωμα λογαριασμού</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Κανένα χρήμα δεν θα μπαίνει ή θα βγαίνει από τον {account && formatIban(account.iban)} μέχρι να ξεπαγώσει. Ο λόγος καταγράφεται.
          </Typography>
          {freeze.isError && <Alert severity="error">Το πάγωμα δεν ολοκληρώθηκε.</Alert>}
          <TextField label="Λόγος" value={reason} onChange={e => setReason(e.target.value)} autoFocus slotProps={{ htmlInput: { maxLength: 200 } }} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={close}>Άκυρο</Button>
        <Button
          variant="contained"
          color="error"
          disabled={!reason.trim() || freeze.isPending}
          onClick={() => account && freeze.mutate({ id: account.id, reason: reason.trim() }, { onSuccess: close })}
        >
          Πάγωμα
        </Button>
      </DialogActions>
    </Dialog>
  )
}

/** Customer accounts for bank staff: search by holder or IBAN; managers can freeze an account. */
export function CustomerAccountsPage() {
  const me = useSession().data
  const canFreeze = !!me && (hasRole(me, 'MANAGER') || hasRole(me, 'ADMIN'))
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const deferred = useDeferredValue(search)
  const accounts = useCustomerAccounts(deferred, page)
  const unfreeze = useFreeze()
  const [freezing, setFreezing] = useState<BankAccount | null>(null)

  return (
    <Stack spacing={3}>
      <PageHeader title="Λογαριασμοί πελατών" subtitle="Όλοι οι λογαριασμοί όψεως των επιχειρήσεων, με υπόλοιπο και κατάσταση." />
      <TextField
        placeholder="Αναζήτηση με επωνυμία ή IBAN"
        value={search}
        onChange={e => {
          setSearch(e.target.value)
          setPage(0)
        }}
        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRoundedIcon /></InputAdornment> }, htmlInput: { 'aria-label': 'Αναζήτηση λογαριασμών' } }}
        sx={{ maxWidth: 480 }}
      />
      {accounts.isError && <Alert severity="error">Δεν ήταν δυνατό να φορτωθούν οι λογαριασμοί.</Alert>}
      {accounts.isPending && <Skeleton variant="rounded" height={240} />}
      {accounts.data && (
        <Card variant="outlined">
          <TableContainer>
            <Table aria-label="Λογαριασμοί πελατών">
              <TableHead>
                <TableRow>
                  <TableCell>Δικαιούχος</TableCell>
                  <TableCell>IBAN</TableCell>
                  <TableCell align="right">Υπόλοιπο</TableCell>
                  <TableCell>Κατάσταση</TableCell>
                  {canFreeze && <TableCell />}
                </TableRow>
              </TableHead>
              <TableBody>
                {accounts.data.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} sx={{ textAlign: 'center', color: 'text.secondary', py: 4 }}>
                      Κανένας λογαριασμός δεν ταιριάζει.
                    </TableCell>
                  </TableRow>
                )}
                {accounts.data.content.map(a => (
                  <TableRow key={a.id} hover>
                    <TableCell>
                      <Typography sx={{ fontWeight: 600 }}>{a.holderName}</Typography>
                      <Typography variant="body2" color="text.secondary">{a.name}</Typography>
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', whiteSpace: 'nowrap' }}>{formatIban(a.iban)}</TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap', fontWeight: 600 }}>
                      {formatMoney(a.balance)}
                    </TableCell>
                    <TableCell>
                      {a.status === 'ACTIVE' ? (
                        <Chip size="small" label="Ενεργός" sx={{ bgcolor: 'success.light', color: 'success.main' }} />
                      ) : (
                        <Chip size="small" label="Παγωμένος" title={a.statusReason ?? undefined} sx={{ bgcolor: 'error.light', color: 'error.main' }} />
                      )}
                    </TableCell>
                    {canFreeze && (
                      <TableCell align="right">
                        {a.status === 'ACTIVE' ? (
                          <Button size="small" color="error" onClick={() => setFreezing(a)}>Πάγωμα</Button>
                        ) : (
                          <Button size="small" disabled={unfreeze.isPending} onClick={() => unfreeze.mutate({ id: a.id, reason: null })}>
                            Ξεπάγωμα
                          </Button>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={accounts.data.page.totalElements}
            page={accounts.data.page.number}
            rowsPerPage={accounts.data.page.size}
            rowsPerPageOptions={[]}
            onPageChange={(_, next) => setPage(next)}
            labelDisplayedRows={({ from, to, count }) => `${from}–${to} από ${count}`}
          />
        </Card>
      )}
      <FreezeDialog account={freezing} onClose={() => setFreezing(null)} />
    </Stack>
  )
}
