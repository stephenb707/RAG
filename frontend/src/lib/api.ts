import type { RagAnswer, ChatMode, IndexResponse, StatusResponse } from './types'

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

export async function postIndex(
  repoName: string,
  rootPath?: string
): Promise<IndexResponse> {
  const baseUrl = getBaseUrl()
  const body: { repoName: string; rootPath?: string } = { repoName }
  if (rootPath && rootPath.trim()) {
    body.rootPath = rootPath.trim()
  }

  const response = await fetch(`${baseUrl}/api/index`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Failed to index: ${response.status} ${errorText}`)
  }

  return await response.json()
}

export async function getStatus(): Promise<StatusResponse> {
  const baseUrl = getBaseUrl()
  const response = await fetch(`${baseUrl}/api/index/status`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
    },
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Failed to get status: ${response.status} ${errorText}`)
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
