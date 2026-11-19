import { Alert, Avatar, Breadcrumbs, Link, Skeleton, Stack, Typography } from '@mui/material'
import { Link as RouterLink, useParams } from 'react-router'
import { colourFor, initials } from '../../app/theme'
import { useBusiness } from '../businesses/api'
import { DocumentsTable } from './DocumentsTable'
import { UploadZone } from './UploadZone'

export function BusinessDocumentsPage() {
  const { businessId = '' } = useParams()
  const business = useBusiness(businessId)

  return (
    <Stack spacing={4}>
      <Stack spacing={2}>
        <Breadcrumbs aria-label="Θέση στην εφαρμογή">
          <Link component={RouterLink} to="/businesses" underline="hover" color="text.secondary">
            Οι επιχειρήσεις μου
          </Link>
          <Typography color="text.primary">{business.data?.name ?? '…'}</Typography>
        </Breadcrumbs>

        {business.isPending && <Skeleton variant="rounded" width={360} height={56} />}
        {business.isError && <Alert severity="error">Η επιχείρηση δεν βρέθηκε.</Alert>}
        {business.data && (
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            <Avatar variant="rounded" sx={{ width: 56, height: 56, fontSize: 20, fontWeight: 700, bgcolor: colourFor(business.data.name) }}>
              {initials(business.data.name)}
            </Avatar>
            <Stack sx={{ minWidth: 0 }}>
              <Typography variant="h4" component="h1" noWrap>
                {business.data.name}
              </Typography>
              <Typography color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                ΑΦΜ {business.data.afm}
              </Typography>
            </Stack>
          </Stack>
        )}
      </Stack>

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
