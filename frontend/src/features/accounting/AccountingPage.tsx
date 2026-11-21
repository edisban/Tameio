import ChevronLeftRoundedIcon from '@mui/icons-material/ChevronLeftRounded'
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded'
import DownloadRoundedIcon from '@mui/icons-material/DownloadRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  FormControlLabel,
  IconButton,
  Link,
  MenuItem,
  Skeleton,
  Stack,
  Switch,
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
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import { useBusiness } from '../businesses/api'
import { BusinessHeader } from '../businesses/BusinessHeader'
import { categoryLabel, categoryLabels } from '../documents/categories'
import { formatDay, formatMoney } from '../documents/files'
import { downloadExpenses, useExpenses, useYearSummary, type YearSummary } from './api'
import { BarList, ColumnChart } from './charts'

const MONTHS = ['Ιαν', 'Φεβ', 'Μαρ', 'Απρ', 'Μάι', 'Ιουν', 'Ιουλ', 'Αύγ', 'Σεπ', 'Οκτ', 'Νοε', 'Δεκ']
const MONTH_NAMES = ['Ιανουάριος', 'Φεβρουάριος', 'Μάρτιος', 'Απρίλιος', 'Μάιος', 'Ιούνιος', 'Ιούλιος', 'Αύγουστος', 'Σεπτέμβριος', 'Οκτώβριος', 'Νοέμβριος', 'Δεκέμβριος']
const num = { fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' } as const

function StatTile({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <Card variant="outlined" sx={{ flex: 1 }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
        <Typography variant="h5" component="p" sx={{ fontWeight: 600, ...num }}>{value}</Typography>
        <Typography variant="caption" color="text.secondary">{note}</Typography>
      </CardContent>
    </Card>
  )
}

const documents = (count: number) => (count === 1 ? '1 παραστατικό' : `${count} παραστατικά`)

function Overview({ summary }: { summary: YearSummary }) {
  const [asTable, setAsTable] = useState(false)
  const { totals } = summary

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <StatTile label="Έξοδα" value={formatMoney(totals.total)} note={`καθαρά ${formatMoney(totals.net)}`} />
        <StatTile label="ΦΠΑ εισροών" value={formatMoney(totals.vat)} note="ο ΦΠΑ που πληρώσατε στις αγορές σας" />
        <StatTile label="Παραστατικά" value={String(totals.count)} note="επιβεβαιωμένα, στο βιβλίο εξόδων" />
      </Stack>

      <Card variant="outlined">
        <CardContent>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="h6" component="h2">Έξοδα ανά μήνα</Typography>
            <FormControlLabel control={<Switch checked={asTable} onChange={e => setAsTable(e.target.checked)} />} label="Πίνακας" />
          </Stack>
          {asTable ? (
            <TableContainer>
              <Table size="small" aria-label="Έξοδα ανά μήνα">
                <TableHead>
                  <TableRow>
                    <TableCell>Μήνας</TableCell>
                    <TableCell align="right">Καθαρό</TableCell>
                    <TableCell align="right">ΦΠΑ</TableCell>
                    <TableCell align="right">Σύνολο</TableCell>
                    <TableCell align="right">Παραστατικά</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {summary.months.map(m => (
                    <TableRow key={m.number}>
                      <TableCell>{MONTH_NAMES[m.number - 1]}</TableCell>
                      <TableCell align="right" sx={num}>{formatMoney(m.totals.net)}</TableCell>
                      <TableCell align="right" sx={num}>{formatMoney(m.totals.vat)}</TableCell>
                      <TableCell align="right" sx={num}>{formatMoney(m.totals.total)}</TableCell>
                      <TableCell align="right" sx={num}>{m.totals.count}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : (
            <ColumnChart
              title={`Έξοδα ανά μήνα το ${summary.year}`}
              columns={summary.months.map(m => ({
                label: MONTHS[m.number - 1] ?? '',
                longLabel: `${MONTH_NAMES[m.number - 1]} ${summary.year}`,
                value: m.totals.total,
                detail: documents(m.totals.count),
              }))}
            />
          )}
        </CardContent>
      </Card>

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: '3fr 2fr' } }}>
        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>Ανά κατηγορία</Typography>
            {summary.categories.length === 0 ? (
              <Typography color="text.secondary">Κανένα έξοδο αυτή τη χρονιά.</Typography>
            ) : (
              <BarList
                bars={summary.categories.map(c => ({
                  label: categoryLabel(c.category),
                  value: c.totals.total,
                  detail: `${documents(c.totals.count)} · ΦΠΑ ${formatMoney(c.totals.vat)}`,
                }))}
              />
            )}
          </CardContent>
        </Card>
        <Card variant="outlined">
          <CardContent>
            <Typography variant="h6" component="h2">ΦΠΑ ανά τρίμηνο</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Ο ΦΠΑ εισροών που εκπίπτει στη δήλωση ΦΠΑ κάθε τριμήνου.
            </Typography>
            <Table size="small" aria-label="ΦΠΑ ανά τρίμηνο">
              <TableHead>
                <TableRow>
                  <TableCell>Τρίμηνο</TableCell>
                  <TableCell align="right">Καθαρό</TableCell>
                  <TableCell align="right">ΦΠΑ</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {summary.quarters.map(q => (
                  <TableRow key={q.number}>
                    <TableCell>{q.number}ο</TableCell>
                    <TableCell align="right" sx={num}>{formatMoney(q.totals.net)}</TableCell>
                    <TableCell align="right" sx={{ ...num, fontWeight: 600 }}>{formatMoney(q.totals.vat)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Box>
    </Stack>
  )
}

function ExpenseBook({ businessId, year }: { businessId: string; year: number }) {
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const expenses = useExpenses(businessId, year, category, page)

  return (
    <Card variant="outlined">
      <CardContent sx={{ pb: 0 }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
          <Typography variant="h6" component="h2">Βιβλίο εξόδων</Typography>
          <TextField
            select
            size="small"
            label="Κατηγορία"
            value={category}
            slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
            onChange={e => {
              setCategory(e.target.value)
              setPage(0)
            }}
            sx={{ minWidth: 240 }}
          >
            <MenuItem value="">Όλες</MenuItem>
            {Object.entries(categoryLabels).map(([code, label]) => (
              <MenuItem key={code} value={code}>{label}</MenuItem>
            ))}
          </TextField>
        </Stack>
      </CardContent>
      {expenses.isError && <Alert severity="error" sx={{ m: 2 }}>Δεν ήταν δυνατό να φορτωθεί το βιβλίο εξόδων.</Alert>}
      {expenses.isPending && <Skeleton variant="rounded" height={160} sx={{ m: 2 }} />}
      {expenses.data && (
        <>
          <TableContainer>
            <Table aria-label="Βιβλίο εξόδων">
              <TableHead>
                <TableRow>
                  <TableCell>Ημερομηνία</TableCell>
                  <TableCell>ΑΦΜ εκδότη</TableCell>
                  <TableCell>Κατηγορία</TableCell>
                  <TableCell align="right">Καθαρό</TableCell>
                  <TableCell align="right">ΦΠΑ</TableCell>
                  <TableCell align="right">Σύνολο</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {expenses.data.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} sx={{ textAlign: 'center', color: 'text.secondary', py: 4 }}>
                      Τα παραστατικά που επιβεβαιώνετε εμφανίζονται εδώ.
                    </TableCell>
                  </TableRow>
                )}
                {expenses.data.content.map(e => (
                  <TableRow key={e.id} hover>
                    <TableCell sx={num}>
                      <Link component={RouterLink} to={`/businesses/${businessId}/documents/${e.documentId}`} underline="hover">
                        {formatDay(e.issueDate)}
                      </Link>
                    </TableCell>
                    <TableCell sx={num}>{e.supplierAfm}</TableCell>
                    <TableCell>{categoryLabel(e.category)}</TableCell>
                    <TableCell align="right" sx={num}>{formatMoney(e.netAmount)}</TableCell>
                    <TableCell align="right" sx={num}>{formatMoney(e.vatAmount)}</TableCell>
                    <TableCell align="right" sx={{ ...num, fontWeight: 600 }}>{formatMoney(e.totalAmount)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={expenses.data.page.totalElements}
            page={expenses.data.page.number}
            rowsPerPage={expenses.data.page.size}
            rowsPerPageOptions={[]}
            onPageChange={(_, next) => setPage(next)}
            labelDisplayedRows={({ from, to, count }) => `${from}–${to} από ${count}`}
          />
        </>
      )}
    </Card>
  )
}

export function AccountingPage() {
  const { businessId = '' } = useParams()
  const business = useBusiness(businessId)
  const [year, setYear] = useState(() => new Date().getFullYear())
  const summary = useYearSummary(businessId, year)
  const [exportError, setExportError] = useState(false)

  async function exportYear() {
    setExportError(false)
    try {
      await downloadExpenses(businessId, year)
    } catch {
      setExportError(true)
    }
  }

  return (
    <Stack spacing={4}>
      <BusinessHeader businessId={businessId} section="accounting" />
      {business.data && (
        <>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', rowGap: 1 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <IconButton aria-label="Προηγούμενη χρονιά" onClick={() => setYear(y => y - 1)}>
                <ChevronLeftRoundedIcon />
              </IconButton>
              <Typography variant="h5" component="p" aria-live="polite" sx={num}>
                {year}
              </Typography>
              <IconButton aria-label="Επόμενη χρονιά" onClick={() => setYear(y => y + 1)} disabled={year >= new Date().getFullYear()}>
                <ChevronRightRoundedIcon />
              </IconButton>
            </Stack>
            <Button variant="outlined" startIcon={<DownloadRoundedIcon />} onClick={() => void exportYear()}>
              Εξαγωγή για τον λογιστή (CSV)
            </Button>
          </Stack>
          {exportError && <Alert severity="error" onClose={() => setExportError(false)}>Η εξαγωγή δεν ολοκληρώθηκε. Δοκιμάστε ξανά.</Alert>}
          {summary.isError && <Alert severity="error">Δεν ήταν δυνατό να φορτωθεί η σύνοψη.</Alert>}
          {summary.isPending && <Skeleton variant="rounded" height={320} />}
          {summary.data && <Overview summary={summary.data} />}
          <ExpenseBook businessId={businessId} year={year} />
        </>
      )}
    </Stack>
  )
}
