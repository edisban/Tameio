import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import ReplayRoundedIcon from '@mui/icons-material/ReplayRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Link,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink } from 'react-router'
import { useDocuments, useReadAgain, type DocumentItem } from './api'
import { categoryLabel } from './categories'
import { formatBytes, formatDateTime, formatDay, formatMoney, typeLabel } from './files'
import { failureLabel, statusColour, statusLabel } from './status'

const PAGE_SIZE = 20

/** A small coloured tile with the file's kind, so PDFs and photos are told apart at a glance. */
function FileTypeTile({ mediaType }: { mediaType: string }) {
  const isPdf = mediaType === 'application/pdf'
  return (
    <Box
      aria-hidden
      sx={{
        width: 36,
        height: 36,
        borderRadius: 1.5,
        flexShrink: 0,
        display: 'grid',
        placeItems: 'center',
        bgcolor: isPdf ? 'error.light' : 'primary.light',
        color: isPdf ? 'error.main' : 'primary.main',
      }}
    >
      {isPdf ? <DescriptionOutlinedIcon fontSize="small" /> : <ImageOutlinedIcon fontSize="small" />}
    </Box>
  )
}

// On phones the name, total and status matter most; the other columns give them their room
const wideOnly = { display: { xs: 'none', md: 'table-cell' } }
const dash = <Typography component="span" color="text.disabled">—</Typography>

function StatusCell({ businessId, document }: { businessId: string; document: DocumentItem }) {
  const readAgain = useReadAgain(businessId)
  const chip = <Chip size="small" label={statusLabel[document.status]} sx={statusColour[document.status]} />
  if (document.status !== 'FAILED') return chip
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Tooltip title={document.failure ? failureLabel[document.failure] : ''}>{chip}</Tooltip>
      <Button
        size="small"
        startIcon={<ReplayRoundedIcon />}
        disabled={readAgain.isPending}
        onClick={() => readAgain.mutate(document.id)}
        aria-label={`Ανάγνωση ξανά: ${document.filename}`}
      >
        Ξανά
      </Button>
    </Stack>
  )
}

export function DocumentsTable({ businessId }: { businessId: string }) {
  const [page, setPage] = useState(0)
  const documents = useDocuments(businessId, page, PAGE_SIZE)

  if (documents.isPending) {
    return (
      <Stack spacing={1}>
        <Skeleton variant="rounded" height={48} />
        <Skeleton variant="rounded" height={48} />
        <Skeleton variant="rounded" height={48} />
      </Stack>
    )
  }

  if (documents.isError) {
    return <Alert severity="error">{documents.error.message}</Alert>
  }

  const { content, page: info } = documents.data

  if (info.totalElements === 0) {
    return (
      <Card variant="outlined">
        <CardContent sx={{ textAlign: 'center', py: 5 }}>
          <Typography variant="h6">Κανένα παραστατικό ακόμα</Typography>
          <Typography color="text.secondary">Τα αρχεία που ανεβάζετε θα εμφανίζονται εδώ, τα νεότερα πρώτα.</Typography>
        </CardContent>
      </Card>
    )
  }

  return (
    <Stack spacing={1}>
      <Card variant="outlined">
        <TableContainer>
          <Table aria-label="Παραστατικά">
            <TableHead>
              <TableRow>
                <TableCell>Αρχείο</TableCell>
                <TableCell sx={wideOnly}>Κατηγορία</TableCell>
                <TableCell sx={wideOnly}>Έκδοση</TableCell>
                <TableCell align="right">Σύνολο</TableCell>
                <TableCell>Κατάσταση</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {content.map(document => (
                <TableRow key={document.id} hover>
                  <TableCell sx={{ minWidth: 200, maxWidth: 360 }}>
                    <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                      <FileTypeTile mediaType={document.mediaType} />
                      <Link
                        component={RouterLink}
                        to={`/businesses/${businessId}/documents/${document.id}`}
                        underline="hover"
                        sx={{ textAlign: 'left', overflowWrap: 'anywhere', fontWeight: 600, color: 'text.primary' }}
                      >
                        {document.filename}
                      </Link>
                    </Stack>
                    <Typography variant="body2" color="text.secondary" sx={{ pl: 6, fontVariantNumeric: 'tabular-nums' }}>
                      {typeLabel(document.mediaType)} · {formatBytes(document.sizeBytes)} · {formatDateTime(document.uploadedAt)}
                    </Typography>
                  </TableCell>
                  <TableCell sx={wideOnly}>{document.summary ? categoryLabel(document.summary.category) : dash}</TableCell>
                  <TableCell sx={{ ...wideOnly, whiteSpace: 'nowrap' }}>
                    {document.summary?.issueDate ? formatDay(document.summary.issueDate) : dash}
                  </TableCell>
                  <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap', fontWeight: 600 }}>
                    {document.summary?.totalAmount != null ? formatMoney(document.summary.totalAmount) : dash}
                  </TableCell>
                  <TableCell>
                    <StatusCell businessId={businessId} document={document} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={info.totalElements}
          page={info.number}
          rowsPerPage={info.size}
          rowsPerPageOptions={[]}
          onPageChange={(_, next) => setPage(next)}
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} από ${count}`}
        />
      </Card>
    </Stack>
  )
}
