import { alpha, createTheme } from '@mui/material'

// Calm, bank-like: deep navy for structure, one clear blue for actions, a cool light background.
// Commissioner for headings (it has Greek), Inter for everything else.
const navy = '#0f1f3a'
const blue = '#2453a6'
const line = '#e3e8f0'

export const theme = createTheme({
  palette: {
    primary: { main: blue, light: '#e8eef9', contrastText: '#ffffff' },
    secondary: { main: navy, contrastText: '#ffffff' },
    success: { main: '#1d7a4f', light: '#e3f3ea' },
    warning: { main: '#a35f0c', light: '#fbefdf' },
    error: { main: '#b42318', light: '#fdecea' },
    background: { default: '#f4f6fa', paper: '#ffffff' },
    text: { primary: '#14213d', secondary: '#5b6478' },
    divider: line,
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: '"Inter", system-ui, sans-serif',
    h1: { fontFamily: '"Commissioner", "Inter", sans-serif' },
    h2: { fontFamily: '"Commissioner", "Inter", sans-serif' },
    h3: { fontFamily: '"Commissioner", "Inter", sans-serif', fontWeight: 700 },
    h4: { fontFamily: '"Commissioner", "Inter", sans-serif', fontWeight: 700, letterSpacing: '-0.01em' },
    h5: { fontFamily: '"Commissioner", "Inter", sans-serif', fontWeight: 700 },
    h6: { fontFamily: '"Commissioner", "Inter", sans-serif', fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
    overline: { fontWeight: 600, letterSpacing: '0.08em' },
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { borderRadius: 8, paddingInline: 16 }, sizeLarge: { paddingBlock: 10 } },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: { root: { borderColor: line, boxShadow: '0 1px 2px rgba(16, 24, 40, 0.04)' } },
    },
    MuiOutlinedInput: { styleOverrides: { root: { backgroundColor: '#ffffff' } } },
    MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
    MuiTableCell: {
      styleOverrides: {
        head: { color: '#5b6478', fontWeight: 600, fontSize: '0.8rem', backgroundColor: '#f8fafc' },
        root: { borderColor: line },
      },
    },
    MuiAlert: { styleOverrides: { root: { borderRadius: 8 } } },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          '&.Mui-selected': { backgroundColor: alpha('#ffffff', 0.12) },
          '&.Mui-selected:hover': { backgroundColor: alpha('#ffffff', 0.16) },
        },
      },
    },
  },
})

/** The same colour for the same name every time, for avatars with initials. */
const avatarColours = ['#2453a6', '#1d7a4f', '#7a4fb4', '#a35f0c', '#0e7490', '#b42318']

export function colourFor(text: string): string {
  const sum = [...text].reduce((total, char) => total + char.charCodeAt(0), 0)
  return avatarColours[sum % avatarColours.length]!
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(word => word[0]!.toUpperCase())
    .join('')
}
