import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import {
  Alert,
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
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { openDocument, useDocuments, type DocumentItem, type DocumentStatus } from './api'
import { formatBytes, formatDateTime, typeLabel } from './files'

const statusLabel: Record<DocumentStatus, string> = {
  UPLOADED: 'Σε αναμονή ανάγνωσης',
}

const PAGE_SIZE = 20

// The icon beside the name already shows the type, so on phones the column gives its room to the name
const typeColumn = { display: { xs: 'none', sm: 'table-cell' } }

export function DocumentsTable({ businessId }: { businessId: string }) {
  const [page, setPage] = useState(0)
  const documents = useDocuments(businessId, page, PAGE_SIZE)
  const [openError, setOpenError] = useState<string | null>(null)

  async function open(document: DocumentItem) {
    setOpenError(null)
    try {
      await openDocument(businessId, document)
    } catch (error) {
      setOpenError((error as Error).message)
    }
  }

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
      {openError && (
        <Alert severity="error" onClose={() => setOpenError(null)}>
          Δεν ήταν δυνατό να ανοίξει το αρχείο: {openError}
        </Alert>
      )}
      <Card variant="outlined">
        <TableContainer>
          <Table aria-label="Παραστατικά">
            <TableHead>
              <TableRow>
                <TableCell>Αρχείο</TableCell>
                <TableCell sx={typeColumn}>Τύπος</TableCell>
                <TableCell align="right">Μέγεθος</TableCell>
                <TableCell>Ημερομηνία</TableCell>
                <TableCell>Κατάσταση</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {content.map(document => (
                <TableRow key={document.id} hover>
                  <TableCell sx={{ minWidth: 200, maxWidth: 360 }}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                      {document.mediaType === 'application/pdf' ? (
                        <DescriptionOutlinedIcon fontSize="small" color="action" />
                      ) : (
                        <ImageOutlinedIcon fontSize="small" color="action" />
                      )}
                      <Link component="button" onClick={() => void open(document)} sx={{ textAlign: 'left', overflowWrap: 'anywhere' }}>
                        {document.filename}
                      </Link>
                    </Stack>
                  </TableCell>
                  <TableCell sx={typeColumn}>{typeLabel(document.mediaType)}</TableCell>
                  <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
                    {formatBytes(document.sizeBytes)}
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(document.uploadedAt)}</TableCell>
                  <TableCell>
                    <Chip size="small" label={statusLabel[document.status]} />
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
