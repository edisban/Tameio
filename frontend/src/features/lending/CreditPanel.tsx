import { Box, Card, CardContent, Stack, Typography } from '@mui/material'
import type { CreditEstimate, CreditMeasure } from './api'

const percent = new Intl.NumberFormat('el-GR', { style: 'percent', maximumFractionDigits: 1 })
const number = new Intl.NumberFormat('el-GR', { maximumFractionDigits: 2 })

/** How each measure reads, and how its value is shown. */
const MEASURES: Record<CreditMeasure, { title: string; show: (value: number) => string }> = {
  repayment_ratio: { title: 'Δόση προς μηνιαίο πλεόνασμα', show: v => percent.format(v) },
  loan_size: { title: 'Δάνειο προς ετήσιες εισροές', show: v => percent.format(v) },
  history_months: { title: 'Μήνες ιστορικού', show: v => `${number.format(v)} μήνες` },
  cash_cushion: { title: 'Δόσεις που καλύπτει το υπόλοιπο', show: v => number.format(v) },
  inflow_volatility: { title: 'Διακύμανση εισροών από μήνα σε μήνα', show: v => percent.format(v) },
  negative_months: { title: 'Μήνες με περισσότερα έξοδα από έσοδα', show: v => percent.format(v) },
}

/**
 * The credit model's estimate beside the scorecard: the chance the loan is not repaid, and how far each measure
 * moved it from an average applicant. Red pushes the estimate up, blue down. It informs; a person decides.
 */
export function CreditPanel({ credit }: { credit: CreditEstimate }) {
  const widest = Math.max(...credit.factors.map(f => Math.abs(f.contribution)), 0.01)
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2}>
          <Box>
            <Typography variant="overline" color="text.secondary">
              Εκτίμηση μοντέλου · έκδοση {credit.modelVersion}
            </Typography>
            <Typography
              variant="h3"
              component="p"
              sx={{ fontVariantNumeric: 'tabular-nums' }}
              aria-label={`Πιθανότητα αθέτησης ${percent.format(credit.defaultProbability)}`}
            >
              {percent.format(credit.defaultProbability)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Πιθανότητα να μην αποπληρωθεί το δάνειο, από λογιστική παλινδρόμηση που εκπαιδεύτηκε σε προσομοιωμένα δάνεια.
              Δεν αντικαθιστά τη βαθμολογία· αποφασίζει πάντα άνθρωπος.
            </Typography>
          </Box>
          <Stack spacing={1.5} component="ul" aria-label="Τι μετακίνησε την εκτίμηση" sx={{ listStyle: 'none', p: 0, m: 0 }}>
            {credit.factors.map(factor => {
              const { title, show } = MEASURES[factor.measure]
              const share = (Math.abs(factor.contribution) / widest) * 50
              const raises = factor.contribution > 0
              return (
                <Box component="li" key={factor.measure}>
                  <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 2 }}>
                    <Typography sx={{ fontWeight: 600 }}>{title}</Typography>
                    <Typography sx={{ fontVariantNumeric: 'tabular-nums' }}>{show(factor.value)}</Typography>
                  </Stack>
                  {/* A bar from the middle: right raises the estimate, left lowers it */}
                  <Box sx={{ position: 'relative', height: 8, my: 0.5, bgcolor: 'action.hover', borderRadius: 4 }} aria-hidden>
                    <Box sx={{ position: 'absolute', left: '50%', top: -2, bottom: -2, width: '1px', bgcolor: 'text.disabled' }} />
                    <Box
                      sx={{
                        position: 'absolute',
                        top: 0,
                        bottom: 0,
                        borderRadius: 4,
                        bgcolor: raises ? 'error.main' : 'info.main',
                        left: raises ? '50%' : `${50 - share}%`,
                        width: `${share}%`,
                      }}
                    />
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    {raises ? 'Ανεβάζει την εκτίμηση' : 'Κατεβάζει την εκτίμηση'}
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
