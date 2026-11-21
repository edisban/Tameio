import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded'
import ReplayRoundedIcon from '@mui/icons-material/ReplayRounded'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Chip,
  CircularProgress,
  Link,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material'
import { Link as RouterLink, useParams } from 'react-router'
import { useBusiness } from '../businesses/api'
import { isPending, openDocument, useDocument, useReadAgain } from './api'
import { DocumentPreview } from './DocumentPreview'
import { formatDateTime } from '../../format'
import { ReviewForm } from './ReviewForm'
import { failureLabel, statusColour, statusLabel } from './status'

/** One document: the file on the left, what was read from it and the review form on the right. */
export function DocumentPage() {
  const { businessId = '', documentId = '' } = useParams()
  const business = useBusiness(businessId)
  const document = useDocument(businessId, documentId)
  const readAgain = useReadAgain(businessId)

  if (document.isError) {
    return <Alert severity="error">Το παραστατικό δεν βρέθηκε.</Alert>
  }

  const doc = document.data

  return (
    <Stack spacing={3}>
      <Breadcrumbs aria-label="Θέση στην εφαρμογή">
        <Link component={RouterLink} to="/businesses" underline="hover" color="text.secondary">
          Οι επιχειρήσεις μου
        </Link>
        <Link component={RouterLink} to={`/businesses/${businessId}`} underline="hover" color="text.secondary">
          {business.data?.name ?? '…'}
        </Link>
        <Typography color="text.primary">{doc?.filename ?? '…'}</Typography>
      </Breadcrumbs>

      {!doc ? (
        <Skeleton variant="rounded" height={480} />
      ) : (
        <>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
            <Stack spacing={0.5} sx={{ minWidth: 0 }}>
              <Typography variant="h5" component="h1" sx={{ overflowWrap: 'anywhere' }}>
                {doc.filename}
              </Typography>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Chip size="small" label={statusLabel[doc.status]} sx={statusColour[doc.status]} />
                <Typography variant="body2" color="text.secondary">
                  Ανέβηκε {formatDateTime(doc.uploadedAt)}
                </Typography>
              </Stack>
            </Stack>
            <Button startIcon={<OpenInNewRoundedIcon />} onClick={() => void openDocument(businessId, doc)}>
              Άνοιγμα αρχείου
            </Button>
          </Stack>

          <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1.1fr) minmax(0, 1fr)' } }}>
            <DocumentPreview businessId={businessId} documentId={doc.id} mediaType={doc.mediaType} filename={doc.filename} />

            <Stack spacing={2}>
              {isPending(doc.status) && (
                <Alert severity="info" icon={<CircularProgress size={18} />}>
                  Το έγγραφο διαβάζεται. Τα στοιχεία θα εμφανιστούν εδώ σε λίγα δευτερόλεπτα.
                </Alert>
              )}
              {doc.status === 'FAILED' && (
                <Alert
                  severity="warning"
                  action={
                    <Button color="inherit" size="small" startIcon={<ReplayRoundedIcon />} disabled={readAgain.isPending}
                      onClick={() => readAgain.mutate(doc.id)}>
                      Ανάγνωση ξανά
                    </Button>
                  }
                >
                  {doc.failure ? failureLabel[doc.failure] : 'Το έγγραφο δεν διαβάστηκε.'} Μπορείτε και να συμπληρώσετε τα στοιχεία μόνοι σας.
                </Alert>
              )}
              {!isPending(doc.status) && <ReviewForm businessId={businessId} document={doc} />}
              {doc.reading && (
                <Accordion variant="outlined" disableGutters>
                  <AccordionSummary>
                    <Typography sx={{ fontWeight: 600 }}>Κείμενο που διαβάστηκε</Typography>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Typography component="pre" variant="body2" sx={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace', m: 0 }}>
                      {doc.reading.text}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {doc.reading.pages} σελ. · {doc.reading.method === 'OCR' ? 'OCR' : 'κείμενο PDF'} · μοντέλο {doc.reading.category.modelVersion}
                    </Typography>
                  </AccordionDetails>
                </Accordion>
              )}
            </Stack>
          </Box>
        </>
      )}
    </Stack>
  )
}
