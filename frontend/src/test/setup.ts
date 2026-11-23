import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { useNotices } from '../app/notices'
import { server } from './server'

// Any request a test did not declare fails the test, instead of silently going nowhere
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  useNotices.getState().dismiss()
  cleanup()
})
afterAll(() => server.close())
