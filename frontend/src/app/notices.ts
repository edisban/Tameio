import { create } from 'zustand'

export type Notice = { id: number; message: string; severity: 'success' | 'error' }

type Notices = {
  notice: Notice | null
  notify: (message: string, severity?: Notice['severity']) => void
  dismiss: () => void
}

let lastId = 0

/**
 * The short message shown after an action, such as "signed in". It lives outside any page because the
 * page that asks for it is often gone a moment later: signing out leaves the menu for the sign-in page.
 * Data from the server stays in TanStack Query; this store holds only what the screen shows.
 */
export const useNotices = create<Notices>()(set => ({
  notice: null,
  // A new id each time, so the same message twice in a row still shows again
  notify: (message, severity = 'success') => set({ notice: { id: ++lastId, message, severity } }),
  dismiss: () => set({ notice: null }),
}))
