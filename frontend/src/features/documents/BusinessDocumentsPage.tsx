import { Stack, Typography } from '@mui/material'
import { useParams } from 'react-router'
import { useBusiness } from '../businesses/api'
import { BusinessHeader } from '../businesses/BusinessHeader'
import { DocumentsTable } from './DocumentsTable'
import { UploadZone } from './UploadZone'

export function BusinessDocumentsPage() {
  const { businessId = '' } = useParams()
  const business = useBusiness(businessId)

  return (
    <Stack spacing={4}>
      <BusinessHeader businessId={businessId} section="documents" />
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
