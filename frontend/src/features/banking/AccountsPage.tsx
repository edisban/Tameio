import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined'
import AddRoundedIcon from '@mui/icons-material/AddRounded'
import SendRoundedIcon from '@mui/icons-material/SendRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../../api/http'
import { useBusiness } from '../businesses/api'
import { BusinessHeader } from '../businesses/BusinessHeader'
import { parseCents } from '../documents/amounts'
import { AccountCard } from './AccountCard'
import { useAccounts, useDemoDeposit, useOpenAccount, useStatement, type BankAccount } from './api'
import { TransactionsList } from './TransactionsList'
import { TransferDialog } from './TransferDialog'

/** For a local stack: money from a pretend customer, so payments can be tried. */
function DemoDepositDialog({ businessId, account, open, onClose }: { businessId: string; account: BankAccount; open: boolean; onClose: () => void }) {
  const deposit = useDemoDeposit(businessId, account.id)
  const [amount, setAmount] = useState('1.000,00')
  const cents = parseCents(amount)
  const valid = cents !== null && cents > 0 && cents <= 1_000_000

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Κατάθεση demo</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Μια πληρωμή από τον «Πελάτη Demo», για να δοκιμάσετε τις μεταφορές. Υπάρχει μόνο στο τοπικό περιβάλλον.
          </Typography>
          {deposit.isError && <Alert severity="error">Η κατάθεση δεν ολοκληρώθηκε.</Alert>}
          <TextField
            label="Ποσό (€)"
            value={amount}
            onChange={e => setAmount(e.target.value)}
            error={!valid}
            helperText={valid ? 'Έως 10.000 € τη φορά' : 'Από 0,01 έως 10.000 €'}
            inputMode="decimal"
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Άκυρο</Button>
        <Button
          variant="contained"
          disabled={!valid || deposit.isPending}
          onClick={() => deposit.mutate(cents! / 100, { onSuccess: onClose })}
        >
          Κατάθεση
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function Statement({ businessId, account }: { businessId: string; account: BankAccount }) {
  const [page, setPage] = useState(0)
  const statement = useStatement(businessId, account.id, page)
  return (
    <TransactionsList data={statement.data} isPending={statement.isPending} isError={statement.isError} onPage={setPage} />
  )
}

export function AccountsPage() {
  const { businessId = '' } = useParams()
  const business = useBusiness(businessId)
  const accounts = useAccounts(businessId)
  const openAccount = useOpenAccount(businessId)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [dialog, setDialog] = useState<'transfer' | 'deposit' | null>(null)

  const list = accounts.data ?? []
  const selected = list.find(a => a.id === selectedId) ?? list[0]

  const open = () => openAccount.mutate('', { onSuccess: account => setSelectedId(account.id) })
  const openError = openAccount.error instanceof ApiError ? openAccount.error.problem.detail : null

  return (
    <Stack spacing={4}>
      <BusinessHeader businessId={businessId} section="accounts" />
      {business.data && (
        <>
          {accounts.isError && <Alert severity="error">Δεν ήταν δυνατό να φορτωθούν οι λογαριασμοί.</Alert>}
          {accounts.isPending && <Skeleton variant="rounded" height={176} sx={{ maxWidth: 420 }} />}
          {openError && <Alert severity="error">{openError}</Alert>}

          {accounts.data && list.length === 0 && (
            <Card variant="outlined">
              <CardContent sx={{ textAlign: 'center', py: 6 }}>
                <AccountBalanceWalletOutlinedIcon sx={{ fontSize: 48, color: 'primary.main' }} />
                <Typography variant="h6" sx={{ mt: 1 }}>
                  Ανοίξτε λογαριασμό στην Kyma Bank
                </Typography>
                <Typography color="text.secondary" sx={{ mb: 3 }}>
                  Αμέσως, με δικό σας IBAN. Πληρώστε προμηθευτές και δείτε τα έσοδά σας δίπλα στα λογιστικά σας.
                </Typography>
                <Button variant="contained" size="large" startIcon={<AddRoundedIcon />} onClick={open} disabled={openAccount.isPending}>
                  Άνοιγμα λογαριασμού
                </Button>
              </CardContent>
            </Card>
          )}

          {selected && (
            <>
              <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', sm: 'repeat(auto-fill, minmax(340px, 1fr))' } }}>
                {list.map(account => (
                  <AccountCard key={account.id} account={account} selected={account.id === selected.id} onSelect={() => setSelectedId(account.id)} />
                ))}
              </Box>

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                <Button
                  variant="contained"
                  size="large"
                  startIcon={<SendRoundedIcon />}
                  onClick={() => setDialog('transfer')}
                  disabled={selected.status !== 'ACTIVE'}
                >
                  Νέα μεταφορά
                </Button>
                <Button variant="outlined" size="large" onClick={() => setDialog('deposit')} disabled={selected.status !== 'ACTIVE'}>
                  Κατάθεση demo
                </Button>
                <Button size="large" startIcon={<AddRoundedIcon />} onClick={open} disabled={openAccount.isPending || list.length >= 5}>
                  Νέος λογαριασμός
                </Button>
              </Stack>
              {selected.status === 'FROZEN' && (
                <Alert severity="warning">Ο λογαριασμός έχει παγώσει από την τράπεζα. Επικοινωνήστε με το κατάστημα.</Alert>
              )}

              <Stack spacing={1.5}>
                <Typography variant="h6" component="h2">
                  Κινήσεις · {selected.name}
                </Typography>
                <Statement key={selected.id} businessId={businessId} account={selected} />
              </Stack>

              <TransferDialog businessId={businessId} account={selected} open={dialog === 'transfer'} onClose={() => setDialog(null)} />
              <DemoDepositDialog businessId={businessId} account={selected} open={dialog === 'deposit'} onClose={() => setDialog(null)} />
            </>
          )}
        </>
      )}
    </Stack>
  )
}
