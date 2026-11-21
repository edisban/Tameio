import ContentCopyRoundedIcon from '@mui/icons-material/ContentCopyRounded'
import { Box, ButtonBase, Chip, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import { useState } from 'react'
import { formatMoney } from '../../format'
import type { BankAccount } from './api'
import { formatIban } from './iban'

/**
 * An account as a card: navy to blue, the balance large, the IBAN in groups of four with a copy button.
 * The selected card is the one whose statement shows below.
 */
export function AccountCard({ account, selected, onSelect }: { account: BankAccount; selected: boolean; onSelect: () => void }) {
  const [copied, setCopied] = useState(false)
  const frozen = account.status !== 'ACTIVE'

  return (
    <ButtonBase
      onClick={onSelect}
      aria-pressed={selected}
      aria-label={`${account.name}, υπόλοιπο ${formatMoney(account.balance)}`}
      sx={{ display: 'block', width: '100%', textAlign: 'left', borderRadius: 3 }}
    >
      <Box
        sx={{
          position: 'relative',
          overflow: 'hidden',
          p: 2.5,
          minHeight: 176,
          borderRadius: 3,
          color: '#ffffff',
          background: frozen
            ? 'linear-gradient(135deg, #4b5563 0%, #6b7280 100%)'
            : 'linear-gradient(135deg, #0f1f3a 0%, #1b3a6e 55%, #2453a6 100%)',
          boxShadow: selected ? '0 0 0 3px #ffffff, 0 0 0 5px #2453a6' : '0 8px 24px rgba(15, 31, 58, 0.18)',
          transition: 'box-shadow 120ms ease',
        }}
      >
        {/* Two soft circles give the card depth without competing with the figures */}
        <Box aria-hidden sx={{ position: 'absolute', right: -40, top: -50, width: 180, height: 180, borderRadius: '50%', bgcolor: 'rgba(255,255,255,0.07)' }} />
        <Box aria-hidden sx={{ position: 'absolute', right: 40, bottom: -70, width: 140, height: 140, borderRadius: '50%', bgcolor: 'rgba(255,255,255,0.05)' }} />

        <Stack spacing={2} sx={{ position: 'relative' }}>
          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography sx={{ fontWeight: 600, opacity: 0.9 }}>{account.name}</Typography>
            {frozen ? (
              <Chip size="small" label="Παγωμένος" sx={{ bgcolor: 'rgba(255,255,255,0.18)', color: '#ffffff' }} />
            ) : (
              <Typography sx={{ fontSize: 12, opacity: 0.7, letterSpacing: '0.08em' }}>TAMEIO</Typography>
            )}
          </Stack>
          <Box>
            <Typography sx={{ fontSize: 12, opacity: 0.75 }}>Διαθέσιμο υπόλοιπο</Typography>
            <Typography sx={{ fontSize: 30, fontWeight: 700, fontVariantNumeric: 'tabular-nums', lineHeight: 1.2 }}>
              {formatMoney(account.balance)}
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography sx={{ fontFamily: 'monospace', fontSize: 14, letterSpacing: '0.04em', opacity: 0.9 }}>
              {formatIban(account.iban)}
            </Typography>
            <Tooltip title={copied ? 'Αντιγράφηκε' : 'Αντιγραφή IBAN'}>
              <IconButton
                component="span"
                size="small"
                aria-label="Αντιγραφή IBAN"
                sx={{ color: 'inherit', opacity: 0.8 }}
                onClick={event => {
                  event.stopPropagation()
                  void navigator.clipboard?.writeText(account.iban)
                  setCopied(true)
                }}
              >
                <ContentCopyRoundedIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Tooltip>
          </Stack>
        </Stack>
      </Box>
    </ButtonBase>
  )
}
