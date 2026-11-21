import { Card, CardContent, Stack, Typography } from '@mui/material'
import { PageHeader } from '../../app/PageHeader'
import { useSession } from '../../auth/session'

/** The back office's front page. Loan applications arrive here with the lending module. */
export function BankHomePage() {
  const me = useSession().data
  return (
    <Stack spacing={3}>
      <PageHeader title="Αιτήσεις δανείων" subtitle={me ? `Καλώς ήρθατε, ${me.name}.` : undefined} />
      <Card variant="outlined">
        <CardContent sx={{ textAlign: 'center', py: 5 }}>
          <Typography variant="h6">Καμία αίτηση ακόμα</Typography>
          <Typography color="text.secondary">Οι αιτήσεις που υποβάλλουν οι επιχειρήσεις θα εμφανίζονται εδώ.</Typography>
        </CardContent>
      </Card>
    </Stack>
  )
}
