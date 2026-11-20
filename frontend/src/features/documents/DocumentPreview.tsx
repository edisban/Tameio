import { Alert, Box, Card, Skeleton } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { apiBlob } from '../../api/http'

/** The document itself, beside what was read from it: a PDF in the browser's viewer, a photo as an image. */
export function DocumentPreview({ businessId, documentId, mediaType, filename }: {
  businessId: string
  documentId: string
  mediaType: string
  filename: string
}) {
  const file = useQuery({
    queryKey: ['documents', businessId, 'file', documentId],
    queryFn: () => apiBlob(`/api/v1/businesses/${businessId}/documents/${documentId}/content`),
    // The file never changes once uploaded
    staleTime: Infinity,
  })
  const [url, setUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!file.data) return
    const objectUrl = URL.createObjectURL(file.data)
    // An object URL is a browser resource: created here with the blob, released by the cleanup below
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [file.data])

  if (file.isError) return <Alert severity="error">Δεν ήταν δυνατό να φορτωθεί το αρχείο.</Alert>
  if (!url) return <Skeleton variant="rounded" height={560} />

  return (
    <Card variant="outlined" sx={{ height: { xs: 420, md: 'calc(100vh - 220px)' }, minHeight: 420, bgcolor: '#eef1f6' }}>
      {mediaType === 'application/pdf' ? (
        <Box component="iframe" src={url} title={`Προεπισκόπηση: ${filename}`} sx={{ border: 0, width: '100%', height: '100%' }} />
      ) : (
        <Box sx={{ height: '100%', overflow: 'auto', display: 'grid', placeItems: 'start center', p: 2 }}>
          <Box component="img" src={url} alt={`Προεπισκόπηση: ${filename}`} sx={{ maxWidth: '100%', boxShadow: 1 }} />
        </Box>
      )}
    </Card>
  )
}
