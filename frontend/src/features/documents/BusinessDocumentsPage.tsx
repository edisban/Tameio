import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded'
import { Alert, Box, Button, Skeleton, Stack, Typography } from '@mui/material'
import { Link, useParams } from 'react-router'
import { useBusiness } from '../businesses/api'
import { DocumentsTable } from './DocumentsTable'
import { UploadZone } from './UploadZone'

export function BusinessDocumentsPage() {
  const { businessId = '' } = useParams()
  const business = useBusiness(businessId)

  return (
    <Stack spacing={3}>
      <Box>
        <Button component={Link} to="/businesses" startIcon={<ArrowBackRoundedIcon />} sx={{ ml: -1, mb: 1 }}>
          Οι επιχειρήσεις μου
        </Button>
        {business.isPending && <Skeleton width={320} height={48} />}
        {business.isError && <Alert severity="error">{business.error.message}</Alert>}
        {business.data && (
          <>
            <Typography variant="h4" component="h1">
              {business.data.name}
            </Typography>
            <Typography color="text.secondary">ΑΦΜ {business.data.afm}</Typography>
          </>
        )}
      </Box>

      {business.data && (
        <>
          <UploadZone businessId={businessId} />
          <Stack spacing={1.5}>
            <Typography variant="h6" component="h2">
              Παραστατικά
            </Typography>
            <DocumentsTable businessId={businessId} />
          </Stack>
        </>
      )}
    </Stack>
  )
}
