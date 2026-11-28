import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { server } from '../../test/server'
import { AssistantDrawer } from './AssistantDrawer'

const url = '*/api/v1/businesses/b1/assistant/messages'

describe('AssistantDrawer', () => {
  it('sends the question and shows the answer with what it looked up and its source', async () => {
    let sent: unknown
    server.use(
      http.post(url, async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({
          reply: 'Το επιτόκιο είναι 7% τον χρόνο.',
          toolsUsed: ['search_bank_documents'],
          sources: [{ source: 'loans.md', section: 'Επιχειρηματικά δάνεια · Ποσά, διάρκεια και επιτόκιο' }],
        })
      }),
    )
    const { user } = renderWithProviders(<AssistantDrawer businessId="b1" open onClose={() => {}} />)

    await user.type(screen.getByLabelText('Η ερώτησή σας'), 'Τι επιτόκιο έχετε;')
    await user.click(screen.getByRole('button', { name: 'Αποστολή' }))

    const chat = within(screen.getByRole('list', { name: 'Συνομιλία' }))
    expect(await chat.findByText('Το επιτόκιο είναι 7% τον χρόνο.')).toBeInTheDocument()
    expect(chat.getByText(/Κοίταξε: όροι τράπεζας/)).toBeInTheDocument()
    expect(chat.getByText(/Πηγή: Επιχειρηματικά δάνεια/)).toBeInTheDocument()
    expect(sent).toEqual({ messages: [{ role: 'user', content: 'Τι επιτόκιο έχετε;' }] })
  })

  it('sends the earlier messages with the next question', async () => {
    const bodies: { messages: unknown[] }[] = []
    server.use(
      http.post(url, async ({ request }) => {
        bodies.push((await request.json()) as { messages: unknown[] })
        return HttpResponse.json({ reply: `Απάντηση ${bodies.length}`, toolsUsed: [], sources: [] })
      }),
    )
    const { user } = renderWithProviders(<AssistantDrawer businessId="b1" open onClose={() => {}} />)

    await user.click(screen.getByText('Πόσα χρήματα έχω στον λογαριασμό;'))
    expect(await screen.findByText('Απάντηση 1')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Η ερώτησή σας'), 'Και πέρυσι;')
    await user.click(screen.getByRole('button', { name: 'Αποστολή' }))

    expect(await screen.findByText('Απάντηση 2')).toBeInTheDocument()
    expect(bodies[1]?.messages).toEqual([
      { role: 'user', content: 'Πόσα χρήματα έχω στον λογαριασμό;' },
      { role: 'assistant', content: 'Απάντηση 1' },
      { role: 'user', content: 'Και πέρυσι;' },
    ])
  })

  it('says so when the assistant cannot answer', async () => {
    server.use(http.post(url, () => HttpResponse.json({ code: 'ASSISTANT_UNAVAILABLE' }, { status: 503 })))
    const { user } = renderWithProviders(<AssistantDrawer businessId="b1" open onClose={() => {}} />)

    await user.click(screen.getByText('Τι επιτόκιο έχουν τα δάνεια;'))

    expect(await screen.findByText(/δεν μπορεί να απαντήσει αυτή τη στιγμή/)).toBeInTheDocument()
  })
})
