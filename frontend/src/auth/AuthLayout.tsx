import { Box, Paper, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { Logo } from '../app/Logo'
import { LowPolyBackground } from '../app/LowPolyBackground'

/** The sign-in and sign-up pages: the form on a card over the faceted background. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', px: 2, py: 6, position: 'relative' }}>
      <LowPolyBackground />
      <Stack spacing={3} sx={{ width: '100%', maxWidth: 440, position: 'relative', zIndex: 1, alignItems: 'center' }}>
        <Box sx={{ color: '#ffffff' }}>
          <Logo size={40} />
        </Box>
        <Paper component="main" elevation={12} sx={{ width: '100%', p: { xs: 3, sm: 5 }, borderRadius: 3 }}>
          <Stack spacing={3}>
            <Box>
              <Typography variant="h4" component="h1">
                {title}
              </Typography>
              <Typography color="text.secondary" sx={{ mt: 0.5 }}>
                {subtitle}
              </Typography>
            </Box>
            {children}
          </Stack>
        </Paper>
        <Typography sx={{ color: 'rgba(255, 255, 255, 0.6)', fontSize: 13 }}>Kyma Bank · demo</Typography>
      </Stack>
    </Box>
  )
}
