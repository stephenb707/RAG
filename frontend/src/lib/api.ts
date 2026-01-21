import type { RagAnswer, ChatMode } from './types'

export function getBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080'
}

export async function postChat(
  mode: ChatMode,
  message: string
): Promise<RagAnswer> {
  const baseUrl = getBaseUrl()
  let endpoint = '/api/chat'
  
  if (mode === 'architecture') {
    endpoint = '/api/chat/explain-architecture'
  } else if (mode === 'codeReview') {
    endpoint = '/api/chat/code-review'
  }

  const response = await fetch(`${baseUrl}${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message }),
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Failed to send message: ${response.status} ${errorText}`)
  }

  return await response.json()
}

export async function postReindex(): Promise<string> {
  const baseUrl = getBaseUrl()
  const response = await fetch(`${baseUrl}/api/reindex`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Failed to reindex: ${response.status} ${errorText}`)
  }

  return await response.text()
}
