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

export type IndexResponse = {
  repositoryId: number
  filesScanned: number
  filesIndexed: number
  filesSkipped: number
  documentsUpserted: number
  chunksCreated: number
  chunksEmbedded: number
  elapsedMsTotal: number
  embeddingError?: string | null
}

export type StatusResponse = {
  repositoryCount: number
  documentCount: number
  chunksTotal: number
  chunksWithEmbedding: number
  chunksMissingEmbedding: number
}
