import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded'
import ErrorRoundedIcon from '@mui/icons-material/ErrorRounded'
import UploadFileRoundedIcon from '@mui/icons-material/UploadFileRounded'
import { Box, Button, CircularProgress, List, ListItem, ListItemIcon, ListItemText, Typography } from '@mui/material'
import { useRef, useState, type DragEvent } from 'react'
import { useUploadDocument } from './api'
import { ACCEPT_ATTRIBUTE, fileProblem, uploadErrorMessage } from './files'

type UploadState = { key: string; name: string } & (
  | { state: 'uploading' }
  | { state: 'done' }
  | { state: 'failed'; message: string }
)

/** Drop files here or pick them; each one is checked, then uploaded in turn, with its outcome listed below. */
export function UploadZone({ businessId }: { businessId: string }) {
  const upload = useUploadDocument(businessId)
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [uploads, setUploads] = useState<UploadState[]>([])

  const update = (key: string, next: UploadState) => setUploads(list => list.map(u => (u.key === key ? next : u)))

  async function uploadAll(files: File[]) {
    const batch = files.map(file => ({ file, key: crypto.randomUUID() }))
    setUploads(list => [...batch.map(({ file, key }) => ({ key, name: file.name, state: 'uploading' as const })), ...list])
    // One at a time: simpler for the server and the list fills in order
    for (const { file, key } of batch) {
      const problem = fileProblem(file)
      if (problem) {
        update(key, { key, name: file.name, state: 'failed', message: problem })
        continue
      }
      try {
        await upload.mutateAsync(file)
        update(key, { key, name: file.name, state: 'done' })
      } catch (error) {
        update(key, { key, name: file.name, state: 'failed', message: uploadErrorMessage(error) })
      }
    }
  }

  function onDrop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    void uploadAll(Array.from(event.dataTransfer.files))
  }

  return (
    <Box>
      <Box
        onDragOver={event => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        sx={{
          border: '2px dashed',
          borderColor: dragging ? 'primary.main' : 'divider',
          bgcolor: dragging ? 'action.hover' : 'background.paper',
          borderRadius: 3,
          p: { xs: 3, sm: 4 },
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 1,
          textAlign: 'center',
          transition: 'border-color 120ms, background-color 120ms',
        }}
      >
        <UploadFileRoundedIcon color="primary" sx={{ fontSize: 40 }} />
        <Typography sx={{ fontWeight: 600 }}>Σύρετε εδώ αποδείξεις, τιμολόγια ή καταστάσεις</Typography>
        <Typography variant="body2" color="text.secondary">
          PDF, JPEG ή PNG, έως 10 MB το καθένα
        </Typography>
        <Button variant="outlined" sx={{ mt: 1 }} onClick={() => input.current?.click()}>
          Επιλογή αρχείων
        </Button>
        <input
          ref={input}
          id="document-files"
          aria-label="Επιλογή αρχείων"
          type="file"
          hidden
          multiple
          accept={ACCEPT_ATTRIBUTE}
          onChange={event => {
            void uploadAll(Array.from(event.target.files ?? []))
            // Allow picking the same file again after an error
            event.target.value = ''
          }}
        />
      </Box>

      {uploads.length > 0 && (
        <List dense aria-label="Πρόσφατα ανεβάσματα">
          {uploads.map(u => (
            <ListItem key={u.key} disableGutters>
              <ListItemIcon sx={{ minWidth: 36 }}>
                {u.state === 'uploading' && <CircularProgress size={20} />}
                {u.state === 'done' && <CheckCircleRoundedIcon color="success" />}
                {u.state === 'failed' && <ErrorRoundedIcon color="error" />}
              </ListItemIcon>
              <ListItemText
                primary={u.name}
                secondary={u.state === 'failed' ? u.message : u.state === 'done' ? 'Ανέβηκε' : 'Ανεβαίνει…'}
                slotProps={{ secondary: { color: u.state === 'failed' ? 'error' : 'text.secondary' } }}
              />
            </ListItem>
          ))}
        </List>
      )}
    </Box>
  )
}
