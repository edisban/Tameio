import { Alert, Box, Button, Chip, Drawer, IconButton, Stack, TextField, Typography } from '@mui/material'
import { type FormEvent, useState } from 'react'
import { type AssistantReply, type ChatMessage, useAsk } from './api'

/** At most this many messages go to the backend, which refuses longer conversations. */
const KEPT = 20

const EXAMPLES = ['Πόσα χρήματα έχω στον λογαριασμό;', 'Πόσο ξόδεψα τους τελευταίους 3 μήνες;', 'Τι επιτόκιο έχουν τα δάνεια;']

const TOOL_LABELS: Record<string, string> = {
  account_overview: 'λογαριασμός',
  expenses_by_category: 'έξοδα',
  loan_status: 'δάνεια',
  search_bank_documents: 'όροι τράπεζας',
}

type Turn = ChatMessage & { details?: Pick<AssistantReply, 'toolsUsed' | 'sources'> }

/**
 * A side panel to ask the assistant about this business. The conversation lives here only; each question sends
 * the last messages, and the answer shows what it looked up and which documents it drew on.
 */
export function AssistantDrawer({ businessId, open, onClose }: { businessId: string; open: boolean; onClose: () => void }) {
  const ask = useAsk(businessId)
  const [turns, setTurns] = useState<Turn[]>([])
  const [question, setQuestion] = useState('')

  function send(text: string) {
    const content = text.trim()
    if (!content || ask.isPending) return
    const conversation = [...turns, { role: 'user' as const, content }]
    setTurns(conversation)
    setQuestion('')
    ask.mutate(
      conversation.slice(-KEPT).map(({ role, content }) => ({ role, content })),
      {
        onSuccess: answer =>
          setTurns(current => [
            ...current,
            { role: 'assistant', content: answer.reply, details: { toolsUsed: answer.toolsUsed, sources: answer.sources } },
          ]),
      },
    )
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    send(question)
  }

  return (
    <Drawer anchor="right" open={open} onClose={onClose} slotProps={{ paper: { sx: { width: { xs: '100%', sm: 420 } } } }}>
      <Stack sx={{ height: '100%', p: 2 }} spacing={2} aria-label="Βοηθός">
        <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
          <Box>
            <Typography variant="h6" component="h2">
              Βοηθός
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Βλέπει μόνο αυτή την επιχείρηση και μόνο διαβάζει.
            </Typography>
          </Box>
          <IconButton onClick={onClose} aria-label="Κλείσιμο">
            ✕
          </IconButton>
        </Stack>

        <Stack spacing={1.5} sx={{ flex: 1, overflowY: 'auto' }} component="ol" aria-label="Συνομιλία" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {turns.length === 0 && (
            <Stack spacing={1} component="li">
              <Typography color="text.secondary">Ρωτήστε για τον λογαριασμό, τα έξοδα, τα δάνεια ή τους όρους της τράπεζας.</Typography>
              {EXAMPLES.map(example => (
                <Chip key={example} label={example} variant="outlined" onClick={() => send(example)} sx={{ alignSelf: 'flex-start' }} />
              ))}
            </Stack>
          )}
          {turns.map((turn, i) => (
            <Box
              component="li"
              key={i}
              sx={{
                alignSelf: turn.role === 'user' ? 'flex-end' : 'flex-start',
                maxWidth: '85%',
                px: 1.5,
                py: 1,
                borderRadius: 2,
                bgcolor: turn.role === 'user' ? 'primary.main' : 'action.hover',
                color: turn.role === 'user' ? 'primary.contrastText' : 'text.primary',
              }}
            >
              <Typography sx={{ whiteSpace: 'pre-wrap' }}>{turn.content}</Typography>
              {turn.details && (turn.details.toolsUsed.length > 0 || turn.details.sources.length > 0) && (
                <Typography variant="caption" color="text.secondary" component="p" sx={{ mt: 0.5 }}>
                  {turn.details.toolsUsed.length > 0 &&
                    `Κοίταξε: ${[...new Set(turn.details.toolsUsed)].map(t => TOOL_LABELS[t] ?? t).join(', ')}`}
                  {turn.details.sources.length > 0 && ` · Πηγή: ${turn.details.sources.map(s => s.section).join('; ')}`}
                </Typography>
              )}
            </Box>
          ))}
          {ask.isPending && (
            <Typography component="li" color="text.secondary" aria-live="polite">
              Ψάχνει…
            </Typography>
          )}
        </Stack>

        {ask.isError && <Alert severity="warning">Ο βοηθός δεν μπορεί να απαντήσει αυτή τη στιγμή. Δοκιμάστε ξανά σε λίγο.</Alert>}

        <Stack component="form" direction="row" spacing={1} onSubmit={submit}>
          <TextField
            label="Η ερώτησή σας"
            value={question}
            onChange={e => setQuestion(e.target.value)}
            size="small"
            fullWidth
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
          <Button type="submit" variant="contained" disabled={!question.trim() || ask.isPending}>
            Αποστολή
          </Button>
        </Stack>
        <Typography variant="caption" color="text.secondary">
          Οι απαντήσεις γράφονται από τεχνητή νοημοσύνη και μπορεί να έχουν λάθη. Δεν εγκρίνει δάνεια ούτε κινεί χρήματα.
        </Typography>
      </Stack>
    </Drawer>
  )
}
