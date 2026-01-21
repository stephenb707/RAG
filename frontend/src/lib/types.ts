export type Citation = {
  filePath: string
  startLine: number
  endLine: number
  snippet: string
}

export type RagAnswer = {
  answer: string
  citations: Citation[]
}

export type ChatMode = 'chat' | 'architecture' | 'codeReview'
