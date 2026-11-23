import AssignmentOutlinedIcon from '@mui/icons-material/AssignmentOutlined'
import BadgeOutlinedIcon from '@mui/icons-material/BadgeOutlined'
import DevicesOtherRoundedIcon from '@mui/icons-material/DevicesOtherRounded'
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined'
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined'
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded'
import MenuRoundedIcon from '@mui/icons-material/MenuRounded'
import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined'
import {
  AppBar,
  Avatar,
  Box,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState, type ReactNode } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import { hasRole, isStaff, useSession, useSignOut, type Me } from '../auth/session'
import { Logo } from './Logo'
import { colourFor, initials } from './theme'

const SIDEBAR_WIDTH = 280

type NavItem = { label: string; icon: ReactNode; to: string }

// Accounts, documents, books and loans are tabs of each business, so one entry leads to all of them
const businessNavigation: NavItem[] = [{ label: 'Επιχειρήσεις', icon: <StorefrontOutlinedIcon />, to: '/businesses' }]

/** The bank's back office: what each member of staff sees depends on their role. */
function navigationFor(me: Me | null | undefined): NavItem[] {
  if (!me || !isStaff(me)) return businessNavigation
  const items: NavItem[] = [
    { label: 'Αιτήσεις δανείων', icon: <AssignmentOutlinedIcon />, to: '/bank' },
    { label: 'Λογαριασμοί πελατών', icon: <AccountBalanceWalletOutlinedIcon />, to: '/bank/accounts' },
  ]
  if (hasRole(me, 'MANAGER') || hasRole(me, 'ADMIN')) {
    items.push({ label: 'Γενικό καθολικό', icon: <MenuBookOutlinedIcon />, to: '/bank/ledger' })
  }
  if (hasRole(me, 'ADMIN')) {
    items.push({ label: 'Προσωπικό', icon: <BadgeOutlinedIcon />, to: '/bank/staff' })
  }
  return items
}

function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const session = useSession()
  const signOut = useSignOut()
  const signOutEverywhere = useSignOut(true)
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const me = session.data

  return (
    <Stack sx={{ height: '100%', bgcolor: 'secondary.main', color: '#ffffff', p: 2 }}>
      <Box sx={{ px: 1, py: 1.5 }}>
        <Logo />
      </Box>

      <List component="nav" aria-label="Κύριο μενού" sx={{ mt: 2, display: 'grid', gap: 0.5 }}>
        {navigationFor(me).map(item => (
          <ListItemButton
            key={item.label}
            component={Link}
            to={item.to}
            selected={item.to === '/bank' ? pathname === '/bank' || pathname.startsWith('/bank/loans') : pathname.startsWith(item.to)}
            onClick={onNavigate}
          >
            <ListItemIcon sx={{ color: 'inherit', minWidth: 36 }}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} slotProps={{ primary: { sx: { fontWeight: 600 } } }} />
          </ListItemButton>
        ))}
      </List>

      <Box sx={{ flexGrow: 1 }} />

      {me && (
        <Stack
          direction="row"
          spacing={1.5}
          sx={{ alignItems: 'center', p: 1.5, borderRadius: 2, bgcolor: 'rgba(255,255,255,0.06)' }}
        >
          <Avatar sx={{ width: 36, height: 36, fontSize: 14, fontWeight: 700, bgcolor: colourFor(me.name) }}>
            {initials(me.name)}
          </Avatar>
          <Box sx={{ minWidth: 0, flexGrow: 1 }}>
            <Typography sx={{ fontWeight: 600, fontSize: 14 }} noWrap>
              {me.name}
            </Typography>
            <Typography sx={{ fontSize: 12, opacity: 0.7 }} noWrap>
              {me.email}
            </Typography>
          </Box>
          <Tooltip title="Αποσύνδεση από όλες τις συσκευές">
            <IconButton
              aria-label="Αποσύνδεση από όλες τις συσκευές"
              size="small"
              sx={{ color: 'inherit' }}
              disabled={signOutEverywhere.isPending}
              onClick={() => signOutEverywhere.mutate(undefined, { onSettled: () => navigate('/sign-in', { replace: true }) })}
            >
              <DevicesOtherRoundedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Tooltip title="Αποσύνδεση">
            <IconButton
              aria-label="Αποσύνδεση"
              size="small"
              sx={{ color: 'inherit' }}
              disabled={signOut.isPending}
              onClick={() => signOut.mutate(undefined, { onSettled: () => navigate('/sign-in', { replace: true }) })}
            >
              <LogoutRoundedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      )}
    </Stack>
  )
}

/** The signed-in app: a navy side menu on wide screens, a top bar with a drawer on phones. */
export function Layout() {
  const [open, setOpen] = useState(false)

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box
        component="aside"
        sx={{ width: SIDEBAR_WIDTH, flexShrink: 0, display: { xs: 'none', md: 'block' }, position: 'sticky', top: 0, height: '100vh' }}
      >
        <Sidebar />
      </Box>

      <Drawer open={open} onClose={() => setOpen(false)} sx={{ display: { md: 'none' } }} slotProps={{ paper: { sx: { width: SIDEBAR_WIDTH } } }}>
        <Sidebar onNavigate={() => setOpen(false)} />
      </Drawer>

      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <AppBar position="sticky" color="secondary" elevation={0} sx={{ display: { md: 'none' } }}>
          <Toolbar sx={{ gap: 1 }}>
            <IconButton color="inherit" edge="start" aria-label="Άνοιγμα μενού" onClick={() => setOpen(true)}>
              <MenuRoundedIcon />
            </IconButton>
            <Logo size={28} />
          </Toolbar>
        </AppBar>
        <Box component="main" sx={{ maxWidth: 1120, mx: 'auto', px: { xs: 2, sm: 4 }, py: { xs: 3, sm: 5 } }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  )
}
