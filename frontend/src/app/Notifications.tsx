import { Alert, Snackbar } from '@mui/material'
import { useNotices } from './notices'

const SHOWN_FOR_MS = 4000

/** Shows the latest notice at the bottom of the screen, whatever page is open. */
export function Notifications() {
  const notice = useNotices(state => state.notice)
  const dismiss = useNotices(state => state.dismiss)

  return (
    <Snackbar
      key={notice?.id}
      open={notice !== null}
      autoHideDuration={SHOWN_FOR_MS}
      onClose={(_, reason) => reason !== 'clickaway' && dismiss()}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
    >
      {notice ? (
        <Alert severity={notice.severity} variant="filled" onClose={dismiss} sx={{ width: '100%' }}>
          {notice.message}
        </Alert>
      ) : undefined}
    </Snackbar>
  )
}
