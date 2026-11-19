import { Box, Stack, Typography } from '@mui/material'

/** The Tameio mark: a till drawer seen from the front, in a rounded square. */
export function Logo({ inverted = false, size = 34 }: { inverted?: boolean; size?: number }) {
  const ink = inverted ? '#0f1f3a' : '#ffffff'
  return (
    <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
      <Box
        aria-hidden
        sx={{
          width: size,
          height: size,
          borderRadius: 2,
          bgcolor: inverted ? '#ffffff' : 'primary.main',
          display: 'grid',
          placeItems: 'center',
          flexShrink: 0,
        }}
      >
        <svg width={size * 0.6} height={size * 0.6} viewBox="0 0 24 24" fill="none">
          <rect x="3" y="5" width="18" height="10" rx="2" stroke={ink} strokeWidth="2" />
          <path d="M3 15h18v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3Z" fill={ink} />
          <path d="M10 18h4" stroke={inverted ? '#ffffff' : '#2453a6'} strokeWidth="1.6" strokeLinecap="round" />
          <path d="M7 9h4M7 12h7" stroke={ink} strokeWidth="1.6" strokeLinecap="round" />
        </svg>
      </Box>
      <Typography
        component="span"
        sx={{ fontFamily: '"Commissioner", sans-serif', fontWeight: 700, fontSize: size * 0.58, color: 'inherit' }}
      >
        Tameio
      </Typography>
    </Stack>
  )
}
