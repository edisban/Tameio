import { useMutation } from '@tanstack/react-query'
import { api } from '../../api/http'

export type ChatMessage = { role: 'user' | 'assistant'; content: string }
export type Source = { source: string; section: string }
export type AssistantReply = { reply: string; toolsUsed: string[]; sources: Source[] }

/** Sends the conversation so far (ending with the question) and gets the assistant's answer. */
export function useAsk(businessId: string) {
  return useMutation({
    mutationFn: (messages: ChatMessage[]) =>
      api<AssistantReply>(`/api/v1/businesses/${businessId}/assistant/messages`, {
        method: 'POST',
        body: JSON.stringify({ messages }),
      }),
  })
}
