import { createTheme } from '@mui/material'

// Calm banking look: ink navy, warm off-white, a muted gold accent
export const theme = createTheme({
  palette: {
    primary: { main: '#1f4b8f' },
    secondary: { main: '#13213a', contrastText: '#ffffff' },
    background: { default: '#f5f3ee', paper: '#ffffff' },
    text: { primary: '#13213a', secondary: '#5b6478' },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: '"Inter", system-ui, sans-serif',
    h4: { fontWeight: 800, letterSpacing: '-0.02em' },
    h6: { fontWeight: 700 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiCard: { styleOverrides: { root: { borderColor: '#e3ded3' } } },
  },
})
