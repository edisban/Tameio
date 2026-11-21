// A small load test: people signing in and reading, as on a busy morning.
//   Half the virtual users are Jack (a business owner) listing his documents; the other half are the
//   credit analyst opening loan cases from the queue. Each signs in once: sign-in hashes the password
//   with Argon2, which is slow on purpose, so signing in on every loop would measure only that.
//
// Run it against the stack started with docker compose (see README, "Several instances"):
//   docker run --rm -i --network tameio_default -e BASE_URL=http://frontend:5180 grafana/k6:1.1.0 run - < load/k6/browse.js
import http from 'k6/http'
import { check, group, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://localhost:5180'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

export const options = {
  // k6 empties the cookie jar between iterations; keep it, so each user stays signed in like a browser
  noCookiesReset: true,
  stages: [
    { duration: '20s', target: 10 },
    { duration: '1m', target: 10 },
    { duration: '10s', target: 0 },
  ],
  // The run fails if more than 1% of requests fail or reads get slow
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:read}': ['p(95)<500'],
  },
}

let signedIn = false

function csrfToken() {
  const cookie = http.cookieJar().cookiesForURL(BASE).tameio_xsrf
  return cookie ? decodeURIComponent(cookie[0]) : ''
}

/** Like the browser: any request sets the CSRF cookie, and sign-in sends it back in a header. */
function signIn(email, password) {
  http.get(`${BASE}/api/v1/me`, { responseCallback: http.expectedStatuses(401) })
  const response = http.post(`${BASE}/api/v1/auth/sign-in`, JSON.stringify({ email, password }), {
    headers: { ...JSON_HEADERS, 'X-XSRF-TOKEN': csrfToken() },
  })
  check(response, { 'signed in': r => r.status === 200 })
}

function read(url) {
  const response = http.get(url, { tags: { kind: 'read' } })
  check(response, { 'read ok': r => r.status === 200 })
  return response
}

function owner() {
  group('business owner lists documents', () => {
    const businesses = read(`${BASE}/api/v1/businesses`).json()
    if (businesses.length > 0) {
      read(`${BASE}/api/v1/businesses/${businesses[0].id}/documents?page=0&size=20`)
    }
  })
}

function analyst() {
  group('analyst opens a loan case', () => {
    const queue = read(`${BASE}/api/v1/bank/loan-applications?page=0&size=25`).json()
    if (queue.content.length > 0) {
      const item = queue.content[Math.floor(Math.random() * queue.content.length)]
      read(`${BASE}/api/v1/bank/loan-applications/${item.id}`)
    }
  })
}

export default function () {
  const isOwner = __VU % 2 === 1
  if (!signedIn) {
    if (isOwner) {
      signIn('jack@tameio.local', 'jack123')
    } else {
      signIn('analyst@tameio.local', 'analyst123')
    }
    signedIn = true
  }
  if (isOwner) {
    owner()
  } else {
    analyst()
  }
  sleep(1)
}
