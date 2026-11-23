import { describe, expect, it } from 'vitest'
import { useNotices } from './notices'

describe('notices', () => {
  it('keeps the latest notice until it is dismissed', () => {
    useNotices.getState().notify('Συνδεθήκατε επιτυχώς.')

    expect(useNotices.getState().notice).toMatchObject({ message: 'Συνδεθήκατε επιτυχώς.', severity: 'success' })
    useNotices.getState().dismiss()
    expect(useNotices.getState().notice).toBeNull()
  })

  it('gives the same message a new id, so it shows again', () => {
    const { notify } = useNotices.getState()

    notify('Αποσυνδεθήκατε επιτυχώς.')
    const first = useNotices.getState().notice?.id
    notify('Αποσυνδεθήκατε επιτυχώς.')

    expect(useNotices.getState().notice?.id).not.toBe(first)
  })
})
