import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded'
import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined'
import { Alert, Avatar, Box, Button, Card, CardActionArea, Chip, Skeleton, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/http'
import { PageHeader } from '../../app/PageHeader'
import { colourFor, initials } from '../../app/theme'
import { useBusinesses, type Business } from './api'
import { RegisterBusinessDialog } from './RegisterBusinessDialog'

const roleLabel = { OWNER: 'Ιδιοκτήτης', ACCOUNTANT: 'Λογιστής' } as const

// Bank staff are signed in but have no businesses; their own screens arrive with the lending phase
const isForbidden = (error: Error) => error instanceof ApiError && error.problem.status === 403

const grid = { display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' } }

function BusinessCard({ business }: { business: Business }) {
  return (
    <Card
      sx={{
        transition: 'border-color 150ms, box-shadow 150ms',
        '&:hover': { borderColor: 'primary.main', boxShadow: '0 4px 16px rgba(36, 83, 166, 0.12)' },
      }}
    >
      <CardActionArea component={Link} to={`/businesses/${business.id}`} sx={{ p: 2.5, height: '100%' }}>
        <Stack spacing={2}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <Avatar variant="rounded" sx={{ width: 44, height: 44, fontWeight: 700, bgcolor: colourFor(business.name) }}>
              {initials(business.name)}
            </Avatar>
            <Chip label={roleLabel[business.role]} size="small" sx={{ bgcolor: 'primary.light', color: 'primary.main' }} />
          </Stack>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="h6" component="h2" noWrap>
              {business.name}
            </Typography>
            <Typography color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums' }}>
              ΑΦΜ {business.afm}
            </Typography>
          </Box>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', color: 'primary.main' }}>
            <Typography sx={{ fontWeight: 600, fontSize: 14 }}>Παραστατικά</Typography>
            <ArrowForwardRoundedIcon fontSize="small" />
          </Stack>
        </Stack>
      </CardActionArea>
    </Card>
  )
}

export function BusinessesPage() {
  const businesses = useBusinesses()
  const [registering, setRegistering] = useState(false)

  return (
    <Stack spacing={4}>
      <PageHeader
        title="Οι επιχειρήσεις μου"
        subtitle="Οι επιχειρήσεις για τις οποίες κρατάτε βιβλία ή ζητάτε χρηματοδότηση."
        action={
          <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setRegistering(true)}>
            Νέα επιχείρηση
          </Button>
        }
      />

      {businesses.isError && (
        <Alert severity={isForbidden(businesses.error) ? 'info' : 'error'}>
          {isForbidden(businesses.error)
            ? 'Ο λογαριασμός σας είναι λογαριασμός τράπεζας και δεν έχει επιχειρήσεις.'
            : 'Οι επιχειρήσεις δεν φορτώθηκαν. Ανανεώστε τη σελίδα.'}
        </Alert>
      )}

      {businesses.isPending && (
        <Box sx={grid}>
          <Skeleton variant="rounded" height={176} />
          <Skeleton variant="rounded" height={176} />
        </Box>
      )}

      {businesses.data?.length === 0 && (
        <Card sx={{ py: 8, px: 3, textAlign: 'center' }}>
          <Stack spacing={2} sx={{ alignItems: 'center' }}>
            <Avatar sx={{ width: 64, height: 64, bgcolor: 'primary.light', color: 'primary.main' }}>
              <StorefrontOutlinedIcon fontSize="large" />
            </Avatar>
            <Box>
              <Typography variant="h6" component="h2">
                Καμία επιχείρηση ακόμα
              </Typography>
              <Typography color="text.secondary" sx={{ maxWidth: 420, mx: 'auto' }}>
                Καταχωρίστε την πρώτη σας επιχείρηση με την επωνυμία και το ΑΦΜ της, για να ανεβάσετε παραστατικά.
              </Typography>
            </Box>
            <Button variant="outlined" startIcon={<AddRoundedIcon />} onClick={() => setRegistering(true)}>
              Καταχώριση επιχείρησης
            </Button>
          </Stack>
        </Card>
      )}

      {!!businesses.data?.length && (
        <Box sx={grid}>
          {businesses.data.map(business => (
            <BusinessCard key={business.id} business={business} />
          ))}
        </Box>
      )}

      <RegisterBusinessDialog open={registering} onClose={() => setRegistering(false)} />
    </Stack>
  )
}
