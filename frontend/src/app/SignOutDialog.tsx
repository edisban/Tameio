import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material'
import { useNavigate } from 'react-router'
import { useSignOut } from '../auth/session'
import { useNotices } from './notices'

export type SignOutScope = 'here' | 'everywhere'

const QUESTION: Record<SignOutScope, { title: string; text: string; confirm: string }> = {
  here: {
    title: 'Αποσύνδεση',
    text: 'Είστε σίγουροι ότι θέλετε να αποσυνδεθείτε;',
    confirm: 'Αποσύνδεση',
  },
  everywhere: {
    title: 'Αποσύνδεση από όλες τις συσκευές',
    text: 'Θα αποσυνδεθείτε εδώ και σε κάθε άλλη συσκευή όπου έχετε συνδεθεί. Είστε σίγουροι;',
    confirm: 'Αποσύνδεση παντού',
  },
}

/** Asks before signing out, then says whether it worked; scope null keeps it closed. */
export function SignOutDialog({ scope, onClose }: { scope: SignOutScope | null; onClose: () => void }) {
  const signOut = useSignOut(scope === 'everywhere')
  const notify = useNotices(state => state.notify)
  const navigate = useNavigate()
  const question = QUESTION[scope ?? 'here']

  async function confirm() {
    try {
      await signOut.mutateAsync()
      notify('Αποσυνδεθήκατε επιτυχώς.')
    } catch {
      // The session is forgotten here either way; say so, since the server did not confirm it
      notify('Η αποσύνδεση δεν επιβεβαιώθηκε από την τράπεζα. Κλείστε τον browser για σιγουριά.', 'error')
    } finally {
      onClose()
      navigate('/sign-in', { replace: true })
    }
  }

  return (
    <Dialog open={scope !== null} onClose={onClose} aria-labelledby="sign-out-title">
      <DialogTitle id="sign-out-title">{question.title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{question.text}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Άκυρο</Button>
        <Button variant="contained" onClick={confirm} disabled={signOut.isPending}>
          {question.confirm}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
