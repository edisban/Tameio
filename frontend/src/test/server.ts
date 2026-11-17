import { setupServer } from 'msw/node'

/**
 * A fake API for component tests. It starts with no routes: each test declares the answers it needs
 * with server.use(...), so what a test depends on is written in the test itself.
 */
export const server = setupServer()
