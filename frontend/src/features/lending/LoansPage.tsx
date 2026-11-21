import AddRoundedIcon from '@mui/icons-material/AddRounded'
import { Alert, Button, Card, CardContent, Skeleton, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { useParams } from 'react-router'
import { useBusiness } from '../businesses/api'
import { BusinessHeader } from '../businesses/BusinessHeader'
import { useAccounts } from '../banking/api'
import { useApplications, useSubmitApplication } from './api'
import { ApplicationCard } from './ApplicationCard'
import { NewApplicationDialog } from './NewApplicationDialog'

/** The business's loans: its applications and where each stands, and a new one. */
export function LoansPage() {
  const { businessId = '' } = useParams()
  const business = useBusiness(businessId)
  const applications = useApplications(businessId)
  const accounts = useAccounts(businessId)
  const submit = useSubmitApplication(businessId)
  const [creating, setCreating] = useState(false)
  const hasAccount = (accounts.data?.length ?? 0) > 0

  return (
    <Stack spacing={4}>
      <BusinessHeader businessId={businessId} section="loans" />
      {business.data && (
        <>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
            <Typography color="text.secondary">
              Η αίτηση συμπληρώνεται από τις κινήσεις του λογαριασμού και τα λογιστικά σας. Αποφασίζουν άνθρωποι της τράπεζας.
            </Typography>
            <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setCreating(true)} disabled={!hasAccount}>
              Νέα αίτηση
            </Button>
          </Stack>
          {accounts.data && !hasAccount && <Alert severity="info">Για δάνειο χρειάζεστε πρώτα λογαριασμό στο Tameio.</Alert>}
          {submit.isError && <Alert severity="error">Η υποβολή δεν ολοκληρώθηκε. Δοκιμάστε ξανά.</Alert>}
          {applications.isError && <Alert severity="error">Δεν ήταν δυνατό να φορτωθούν οι αιτήσεις.</Alert>}
          {applications.isPending && <Skeleton variant="rounded" height={160} />}
          {applications.data?.length === 0 && (
            <Card variant="outlined">
              <CardContent sx={{ textAlign: 'center', py: 5 }}>
                <Typography variant="h6">Καμία αίτηση ακόμα</Typography>
                <Typography color="text.secondary">Οι αιτήσεις σας και η πορεία τους θα εμφανίζονται εδώ.</Typography>
              </CardContent>
            </Card>
          )}
          <Stack spacing={2}>
            {applications.data?.map(application => (
              <ApplicationCard
                key={application.id}
                application={application}
                submitting={submit.isPending}
                onSubmit={() => submit.mutate(application.id)}
              />
            ))}
          </Stack>
          {accounts.data && hasAccount && (
            <NewApplicationDialog businessId={businessId} accounts={accounts.data} open={creating} onClose={() => setCreating(false)} />
          )}
        </>
      )}
    </Stack>
  )
}
