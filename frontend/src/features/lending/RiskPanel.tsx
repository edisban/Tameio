import { Box, Card, CardContent, Chip, LinearProgress, Stack, Typography } from '@mui/material'
import type { Case } from './api'
import { bandColour, bandLabel, explain } from './labels'

/**
 * The score and the four factors behind it, each with what was measured and the points it earned, so
 * the analyst and the manager can see, and tell the customer, why the score is what it is.
 */
export function RiskPanel({ loan }: { loan: Case }) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2}>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            <Typography variant="h3" component="p" sx={{ fontVariantNumeric: 'tabular-nums' }} aria-label={`Βαθμός κινδύνου ${loan.riskScore} από 100`}>
              {loan.riskScore}
              <Typography component="span" color="text.secondary" sx={{ fontSize: 18 }}>
                /100
              </Typography>
            </Typography>
            <Chip label={bandLabel[loan.riskBand]} sx={bandColour[loan.riskBand]} />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Βοηθά την απόφαση, δεν την παίρνει: αποφασίζει πάντα άνθρωπος.
          </Typography>
          <Stack spacing={1.75} component="ul" aria-label="Παράγοντες κινδύνου" sx={{ listStyle: 'none', p: 0, m: 0 }}>
            {loan.riskFactors.map(factor => {
              const { title, detail } = explain(factor)
              return (
                <Box component="li" key={factor.code}>
                  <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                    <Typography sx={{ fontWeight: 600 }}>{title}</Typography>
                    <Typography sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {factor.points}/{factor.maxPoints}
                    </Typography>
                  </Stack>
                  <LinearProgress
                    variant="determinate"
                    value={(factor.points / factor.maxPoints) * 100}
                    aria-hidden
                    sx={{ height: 6, borderRadius: 3, my: 0.5 }}
                  />
                  <Typography variant="body2" color="text.secondary">
                    {detail}
                  </Typography>
                </Box>
              )
            })}
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  )
}
