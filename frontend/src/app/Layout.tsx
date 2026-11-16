import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import { AppBar, Box, Button, Container, Toolbar, Typography } from '@mui/material'
import { useAuth } from 'react-oidc-context'
import { Outlet } from 'react-router'

export function Layout() {
  const auth = useAuth()
  const name = auth.user?.profile.name ?? auth.user?.profile.preferred_username

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" elevation={0} color="secondary">
        <Container maxWidth="lg">
          <Toolbar disableGutters sx={{ gap: 2 }}>
            <Typography variant="h6" component="span" sx={{ fontWeight: 800, flexGrow: 1 }}>
              Tameio
            </Typography>
            <Typography sx={{ display: { xs: 'none', sm: 'block' }, opacity: 0.85 }}>{name}</Typography>
            <Button color="inherit" startIcon={<LogoutRoundedIcon />} onClick={() => auth.signoutRedirect()}>
              Αποσύνδεση
            </Button>
          </Toolbar>
        </Container>
      </AppBar>
      <Container maxWidth="lg" component="main" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  )
}
