import { Box, Stack, Tooltip, Typography, useTheme } from '@mui/material'
import { useEffect, useRef, useState } from 'react'
import { formatMoney } from '../../format'

/** Clean axis steps: 1, 2 or 5 times a power of ten, so ticks read 0 / 200 / 400. */
export function niceMax(value: number, ticks = 4): { max: number; step: number } {
  if (value <= 0) return { max: ticks, step: 1 }
  const rough = value / ticks
  const power = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 5, 10].map(m => m * power).find(s => s >= rough)!
  return { max: step * Math.ceil(value / step), step }
}

const compact = new Intl.NumberFormat('el-GR', {
  notation: 'compact',
  maximumFractionDigits: 1,
})
const whole = new Intl.NumberFormat('el-GR', { maximumFractionDigits: 0 })

/** One axis style: in full under 10.000, compact above. */
export const axisLabel = (value: number, max: number) => (max >= 10_000 ? compact.format(value) : whole.format(value))

/** A rect with 4px rounded corners at the data end only, square at the baseline. */
function columnPath(x: number, y: number, width: number, height: number, r = 4) {
  const radius = Math.min(r, height, width / 2)
  return `M${x},${y + height} V${y + radius} Q${x},${y} ${x + radius},${y} H${x + width - radius} Q${x + width},${y} ${x + width},${y + radius} V${y + height} Z`
}

export type Column = {
  label: string
  longLabel: string
  value: number
  detail: string
}

/**
 * A single-series column chart: one hue, no legend (the heading names the series), thin columns
 * capped at 24px, hairline grid, and a tooltip on each column whose hit area is the whole band.
 */
export function ColumnChart({ columns, title }: { columns: Column[]; title: string }) {
  const theme = useTheme()
  // Drawn at the container's real width, so a column is 24px on screen and text is 11px, not scaled
  const box = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(640)
  useEffect(() => {
    const element = box.current
    if (!element || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(([entry]) => entry && setWidth(Math.max(280, Math.round(entry.contentRect.width))))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const height = 240
  const pad = { top: 12, right: 8, bottom: 26, left: 48 }
  const plotW = width - pad.left - pad.right
  const plotH = height - pad.top - pad.bottom
  const { max, step } = niceMax(Math.max(...columns.map(c => c.value)))
  const band = plotW / columns.length
  const barW = Math.min(24, band * 0.6)
  const y = (v: number) => pad.top + plotH - (v / max) * plotH
  const ticks = Array.from({ length: Math.round(max / step) + 1 }, (_, i) => i * step)

  return (
    <Box ref={box}>
      <Box
        component="svg"
        width={width}
        height={height}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={title}
        sx={{ display: 'block', maxWidth: '100%' }}
      >
        {ticks.map(t => (
          <g key={t}>
            <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} stroke={theme.palette.divider} strokeWidth={1} />
            <text x={pad.left - 8} y={y(t)} dy="0.32em" textAnchor="end" fontSize={11} fill={theme.palette.text.secondary}>
              {axisLabel(t, max)}
            </text>
          </g>
        ))}
        {columns.map((c, i) => {
          const x = pad.left + i * band
          const h = (c.value / max) * plotH
          return (
            <Tooltip
              key={c.label}
              arrow
              title={
                <Stack>
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    {formatMoney(c.value)}
                  </Typography>
                  <Typography variant="caption">
                    {c.longLabel} · {c.detail}
                  </Typography>
                </Stack>
              }
            >
              <g tabIndex={0} aria-label={`${c.longLabel}: ${formatMoney(c.value)}`} style={{ outline: 'none' }}>
                {/* The hit area is the whole band, far bigger than a thin column */}
                <rect x={x} y={pad.top} width={band} height={plotH} fill="transparent" />
                {h > 0 && <path d={columnPath(x + (band - barW) / 2, y(c.value), barW, h)} fill={theme.palette.primary.main} />}
                <text x={x + band / 2} y={height - 8} textAnchor="middle" fontSize={11} fill={theme.palette.text.secondary}>
                  {c.label}
                </text>
              </g>
            </Tooltip>
          )
        })}
      </Box>
    </Box>
  )
}

export type Bar = { label: string; value: number; detail: string }

/** Ranked horizontal bars, one hue, the value at each bar's tip; built from HTML so long Greek labels wrap. */
export function BarList({ bars }: { bars: Bar[] }) {
  const max = Math.max(...bars.map(b => b.value), 0)
  return (
    <Stack spacing={1.5} component="ul" sx={{ listStyle: 'none', p: 0, m: 0 }}>
      {bars.map(b => (
        <Tooltip key={b.label} title={b.detail} placement="top-start">
          <Box
            component="li"
            tabIndex={0}
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: '180px 1fr' },
              gap: { xs: 0.5, sm: 2 },
              alignItems: 'center',
            }}
          >
            <Typography variant="body2">{b.label}</Typography>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
              <Box
                sx={{
                  height: 16,
                  width: `calc(${max ? (b.value / max) * 100 : 0}% - 90px)`,
                  minWidth: 4,
                  bgcolor: 'primary.main',
                  borderRadius: '0 4px 4px 0',
                }}
              />
              <Typography
                variant="body2"
                sx={{
                  fontVariantNumeric: 'tabular-nums',
                  fontWeight: 600,
                  whiteSpace: 'nowrap',
                }}
              >
                {formatMoney(b.value)}
              </Typography>
            </Stack>
          </Box>
        </Tooltip>
      ))}
    </Stack>
  )
}
