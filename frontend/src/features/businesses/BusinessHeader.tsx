import { Alert, Avatar, Breadcrumbs, Link, Skeleton, Stack, Tab, Tabs, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router'
import { colourFor, initials } from '../../app/theme'
import { useBusiness } from './api'

export type BusinessSection = 'accounts' | 'documents' | 'accounting' | 'loans'

const sections: { value: BusinessSection; label: string; path: string }[] = [
  { value: 'accounts', label: 'Λογαριασμοί', path: '/accounts' },
  { value: 'documents', label: 'Παραστατικά', path: '' },
  { value: 'accounting', label: 'Λογιστικά', path: '/accounting' },
  { value: 'loans', label: 'Δάνεια', path: '/loans' },
]

/** The business's name and ΑΦΜ, and tabs for its sections. Renders the section only once the business is known. */
export function BusinessHeader({ businessId, section }: { businessId: string; section: BusinessSection }) {
  const business = useBusiness(businessId)

  return (
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
        <>
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
          <Tabs value={section} aria-label="Ενότητες επιχείρησης" sx={{ borderBottom: 1, borderColor: 'divider' }}>
            {sections.map(s => (
              <Tab key={s.value} value={s.value} label={s.label} component={RouterLink} to={`/businesses/${businessId}${s.path}`} />
            ))}
          </Tabs>
        </>
      )}
    </Stack>
  )
}
