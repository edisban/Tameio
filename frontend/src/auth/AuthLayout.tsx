import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import { Box, Button, Paper, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { Link as RouterLink, useLocation } from 'react-router'
import { Logo } from '../app/Logo'
import { LowPolyBackground } from '../app/LowPolyBackground'

const onGlass = { color: '#ffffff' }

function Header() {
  const { pathname } = useLocation()
  const onSignUp = pathname === '/sign-up'
  return (
    <Box component="header" sx={{ position: 'relative', zIndex: 1, px: { xs: 2, sm: 4 }, py: 2 }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', maxWidth: 1120, mx: 'auto' }}>
        <Box sx={onGlass}>
          <Logo size={32} />
        </Box>
        <Stack direction="row" spacing={3} sx={{ alignItems: 'center' }}>
          <Typography sx={{ ...onGlass, opacity: 0.8, fontSize: 14, display: { xs: 'none', md: 'block' } }}>
            Ψηφιακή τραπεζική για επιχειρήσεις
          </Typography>
          <Button
            component={RouterLink}
            to={onSignUp ? '/sign-in' : '/sign-up'}
            variant="outlined"
            size="small"
            sx={{ color: '#ffffff', borderColor: 'rgba(255,255,255,0.5)', '&:hover': { borderColor: '#ffffff', bgcolor: 'rgba(255,255,255,0.08)' } }}
          >
            {onSignUp ? 'Σύνδεση' : 'Άνοιγμα λογαριασμού'}
          </Button>
        </Stack>
      </Stack>
    </Box>
  )
}

function Footer() {
  return (
    <Box component="footer" sx={{ position: 'relative', zIndex: 1, px: { xs: 2, sm: 4 }, py: 2.5, borderTop: '1px solid rgba(255,255,255,0.12)' }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, maxWidth: 1120, mx: 'auto' }}
      >
        <Typography sx={{ ...onGlass, opacity: 0.7, fontSize: 13 }}>© 2026 Tameio · demo</Typography>
        <Typography sx={{ ...onGlass, opacity: 0.55, fontSize: 12 }}>
          Portfolio project: δεν είναι πραγματική τράπεζα και δεν διαχειρίζεται πραγματικά χρήματα.
        </Typography>
      </Stack>
    </Box>
  )
}

/** The sign-in and sign-up pages: a header, a compact form on a card over the faceted background, a footer. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', gridTemplateRows: 'auto 1fr auto', position: 'relative' }}>
      <LowPolyBackground />
      <Header />
      <Box sx={{ display: 'grid', placeItems: 'center', px: 2, py: 4, position: 'relative', zIndex: 1 }}>
        <Paper component="main" elevation={12} sx={{ width: '100%', maxWidth: 380, p: { xs: 3, sm: 3.5 }, borderRadius: 3 }}>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h5" component="h1">
                {title}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                {subtitle}
              </Typography>
            </Box>
            {children}
            <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', justifyContent: 'center', color: 'text.secondary' }}>
              <LockOutlinedIcon sx={{ fontSize: 14 }} />
              <Typography variant="caption">Ασφαλής σύνδεση · κρυπτογραφημένη συνεδρία</Typography>
            </Stack>
          </Stack>
        </Paper>
      </Box>
      <Footer />
    </Box>
  )
}
