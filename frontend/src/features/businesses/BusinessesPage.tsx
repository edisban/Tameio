import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ChevronRightRoundedIcon from '@mui/icons-material/ChevronRightRounded'
import StorefrontRoundedIcon from '@mui/icons-material/StorefrontRounded'
import { Alert, Box, Button, Card, CardActionArea, CardContent, Chip, Skeleton, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { Link } from 'react-router'
import { useBusinesses } from './api'
import { RegisterBusinessDialog } from './RegisterBusinessDialog'

const roleLabel = { OWNER: 'Ιδιοκτήτης', ACCOUNTANT: 'Λογιστής' } as const

export function BusinessesPage() {
  const businesses = useBusinesses()
  const [registering, setRegistering] = useState(false)

  return (
    <Stack spacing={3}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography variant="h4" component="h1">
            Οι επιχειρήσεις μου
          </Typography>
          <Typography color="text.secondary">Οι επιχειρήσεις για τις οποίες κρατάτε βιβλία ή ζητάτε χρηματοδότηση.</Typography>
        </Box>
        <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setRegistering(true)}>
          Νέα επιχείρηση
        </Button>
      </Stack>

      {businesses.isError && <Alert severity="error">{businesses.error.message}</Alert>}

      {businesses.isPending && (
        <Stack spacing={2}>
          <Skeleton variant="rounded" height={88} />
          <Skeleton variant="rounded" height={88} />
        </Stack>
      )}

      {businesses.data?.length === 0 && (
        <Card variant="outlined">
          <CardContent sx={{ textAlign: 'center', py: 6 }}>
            <StorefrontRoundedIcon color="primary" sx={{ fontSize: 40 }} />
            <Typography variant="h6">Καμία επιχείρηση ακόμα</Typography>
            <Typography color="text.secondary">Καταχωρίστε την πρώτη σας επιχείρηση με την επωνυμία και το ΑΦΜ της.</Typography>
          </CardContent>
        </Card>
      )}

      <Stack spacing={2}>
        {businesses.data?.map(business => (
          <Card key={business.id} variant="outlined">
            <CardActionArea component={Link} to={`/businesses/${business.id}`}>
              <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
                <Box sx={{ flex: 1, minWidth: 200 }}>
                  <Typography variant="h6">{business.name}</Typography>
                  <Typography color="text.secondary">ΑΦΜ {business.afm}</Typography>
                </Box>
                <Chip label={roleLabel[business.role]} color="primary" variant="outlined" />
                <ChevronRightRoundedIcon color="action" />
              </CardContent>
            </CardActionArea>
          </Card>
        ))}
      </Stack>

      <RegisterBusinessDialog open={registering} onClose={() => setRegistering(false)} />
    </Stack>
  )
}
