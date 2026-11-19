import { Box } from '@mui/material'

const WIDTH = 1600
const HEIGHT = 1000
const COLUMNS = 14
const ROWS = 9

type Point = { x: number; y: number; z: number }
type Rgb = [number, number, number]

const DARK: Rgb = [11, 24, 48]
const LIGHT: Rgb = [44, 92, 176]
// Where the light comes from: above, a little to the left, towards the viewer
const SUN = normalise({ x: -0.35, y: -0.55, z: 1 })

/** The same pseudo-random numbers on every render, so the picture never jumps. */
function seeded(seed: number) {
  return () => {
    seed = (seed * 16807) % 2147483647
    return (seed - 1) / 2147483646
  }
}

function normalise(v: { x: number; y: number; z: number }) {
  const length = Math.hypot(v.x, v.y, v.z)
  return { x: v.x / length, y: v.y / length, z: v.z / length }
}

/** How brightly the sun lights a flat triangle, from 0 to 1. */
function lightOn(a: Point, b: Point, c: Point): number {
  const u = { x: b.x - a.x, y: b.y - a.y, z: b.z - a.z }
  const v = { x: c.x - a.x, y: c.y - a.y, z: c.z - a.z }
  let normal = normalise({ x: u.y * v.z - u.z * v.y, y: u.z * v.x - u.x * v.z, z: u.x * v.y - u.y * v.x })
  if (normal.z < 0) normal = { x: -normal.x, y: -normal.y, z: -normal.z }
  return Math.max(0, normal.x * SUN.x + normal.y * SUN.y + normal.z * SUN.z)
}

function colour(a: Point, b: Point, c: Point): string {
  // Lighter towards the top right, then shaded by how each facet faces the sun
  const cx = (a.x + b.x + c.x) / 3 / WIDTH
  const cy = (a.y + b.y + c.y) / 3 / HEIGHT
  const position = Math.min(1, Math.max(0, cx * 0.7 + (1 - cy) * 0.5 - 0.1))
  const shade = 0.55 + lightOn(a, b, c) * 0.6
  const [red, green, blue] = DARK.map((dark, i) => {
    const light = LIGHT[i as 0 | 1 | 2]
    return Math.min(255, Math.round((dark + (light - dark) * position) * shade))
  })
  return `rgb(${red}, ${green}, ${blue})`
}

function facets() {
  const random = seeded(20261117)
  const cellWidth = WIDTH / COLUMNS
  const cellHeight = HEIGHT / ROWS
  const points: Point[] = []
  for (let row = 0; row <= ROWS; row++) {
    for (let column = 0; column <= COLUMNS; column++) {
      // Points on the edge stay on it, so the picture has no gaps at the borders
      const jitterX = column === 0 || column === COLUMNS ? 0 : (random() - 0.5) * cellWidth * 0.8
      const jitterY = row === 0 || row === ROWS ? 0 : (random() - 0.5) * cellHeight * 0.8
      points.push({ x: column * cellWidth + jitterX, y: row * cellHeight + jitterY, z: random() * 90 })
    }
  }
  const at = (row: number, column: number): Point => {
    const point = points[row * (COLUMNS + 1) + column]
    if (!point) throw new Error(`No point at ${row}, ${column}`)
    return point
  }

  const triangles: { points: string; fill: string }[] = []
  const add = (a: Point, b: Point, c: Point) =>
    triangles.push({ points: [a, b, c].map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' '), fill: colour(a, b, c) })
  for (let row = 0; row < ROWS; row++) {
    for (let column = 0; column < COLUMNS; column++) {
      const topLeft = at(row, column)
      const topRight = at(row, column + 1)
      const bottomLeft = at(row + 1, column)
      const bottomRight = at(row + 1, column + 1)
      // Cutting cells along either diagonal at random hides the grid underneath
      if (random() < 0.5) {
        add(topLeft, topRight, bottomRight)
        add(topLeft, bottomRight, bottomLeft)
      } else {
        add(topLeft, topRight, bottomLeft)
        add(topRight, bottomRight, bottomLeft)
      }
    }
  }
  return triangles
}

// Computed once: the picture is the same on every page and every render
const triangles = facets()

/** A faceted, three-dimensional looking surface behind the whole page; purely decorative. */
export function LowPolyBackground() {
  return (
    <Box
      component="svg"
      aria-hidden
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="xMidYMid slice"
      sx={{ position: 'fixed', inset: 0, width: '100%', height: '100%', zIndex: 0, bgcolor: 'secondary.main' }}
    >
      {triangles.map(triangle => (
        <polygon
          key={triangle.points}
          points={triangle.points}
          fill={triangle.fill}
          stroke="rgba(255, 255, 255, 0.07)"
          strokeWidth={1}
          strokeLinejoin="round"
        />
      ))}
    </Box>
  )
}
