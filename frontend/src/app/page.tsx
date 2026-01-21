'use client'

import { useState } from 'react'
import { postChat, postReindex } from '@/lib/api'
import type { ChatMode, Citation, RagAnswer } from '@/lib/types'

interface Message {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
}

export default function Home() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [mode, setMode] = useState<ChatMode>('chat')
  const [reindexLoading, setReindexLoading] = useState(false)
  const [reindexMessage, setReindexMessage] = useState<string | null>(null)

  const sendMessage = async () => {
    if (!input.trim() || loading) return

    const messageText = input.trim()
    const userMessage: Message = { role: 'user', content: messageText }
    setMessages((prev) => [...prev, userMessage])
    setInput('')
    setLoading(true)

    try {
      const data: RagAnswer = await postChat(mode, messageText)
      const assistantMessage: Message = {
        role: 'assistant',
        content: data.answer,
        citations: data.citations,
      }
      setMessages((prev) => [...prev, assistantMessage])
    } catch (error) {
      console.error('Error sending message:', error)
      const errorMessage: Message = {
        role: 'assistant',
        content: `Error: ${error instanceof Error ? error.message : 'Failed to get response from server'}`,
      }
      setMessages((prev) => [...prev, errorMessage])
    } finally {
      setLoading(false)
    }
  }

  const handleReindex = async () => {
    setReindexLoading(true)
    setReindexMessage(null)

    try {
      const result = await postReindex()
      setReindexMessage(result || 'Reindex started successfully')
    } catch (error) {
      console.error('Error reindexing:', error)
      setReindexMessage(
        `Error: ${error instanceof Error ? error.message : 'Failed to start reindex'}`
      )
    } finally {
      setReindexLoading(false)
    }
  }

  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  const CitationItem = ({ citation }: { citation: Citation }) => {
    return (
      <details style={{ marginTop: '8px', marginLeft: '16px' }}>
        <summary
          style={{
            cursor: 'pointer',
            color: '#1976d2',
            fontWeight: '500',
            fontSize: '14px',
          }}
        >
          {citation.filePath} ({citation.startLine}-{citation.endLine})
        </summary>
        <pre
          style={{
            marginTop: '8px',
            padding: '12px',
            backgroundColor: '#f5f5f5',
            border: '1px solid #ddd',
            borderRadius: '4px',
            fontSize: '12px',
            overflowX: 'auto',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          {citation.snippet}
        </pre>
      </details>
    )
  }

  const modeLabels: Record<ChatMode, string> = {
    chat: 'Chat',
    architecture: 'Explain Architecture',
    codeReview: 'Code Review',
  }

  return (
    <main
      style={{
        maxWidth: '800px',
        margin: '0 auto',
        padding: '20px',
        height: '100vh',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '20px',
        }}
      >
        <h1 style={{ margin: 0 }}>RAG Chat</h1>
        <button
          onClick={handleReindex}
          disabled={reindexLoading}
          style={{
            padding: '8px 16px',
            backgroundColor: '#4caf50',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: reindexLoading ? 'not-allowed' : 'pointer',
            fontSize: '14px',
            opacity: reindexLoading ? 0.6 : 1,
          }}
        >
          {reindexLoading ? 'Re-indexing...' : 'Re-index repo'}
        </button>
      </div>

      {reindexMessage && (
        <div
          style={{
            padding: '12px',
            marginBottom: '16px',
            borderRadius: '4px',
            backgroundColor: reindexMessage.startsWith('Error')
              ? '#ffebee'
              : '#e8f5e9',
            color: reindexMessage.startsWith('Error') ? '#c62828' : '#2e7d32',
            fontSize: '14px',
          }}
        >
          {reindexMessage}
        </div>
      )}

      <div
        style={{
          display: 'flex',
          gap: '8px',
          marginBottom: '16px',
          flexWrap: 'wrap',
        }}
      >
        {(Object.keys(modeLabels) as ChatMode[]).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            style={{
              padding: '8px 16px',
              backgroundColor: mode === m ? '#1976d2' : '#e0e0e0',
              color: mode === m ? 'white' : '#333',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '14px',
              fontWeight: mode === m ? '600' : '400',
            }}
          >
            {modeLabels[m]}
          </button>
        ))}
        <span
          style={{
            padding: '8px 12px',
            backgroundColor: '#fff3cd',
            color: '#856404',
            borderRadius: '4px',
            fontSize: '12px',
            display: 'flex',
            alignItems: 'center',
          }}
        >
          Mode: {modeLabels[mode]}
        </span>
      </div>

      <div
        style={{
          flex: 1,
          overflowY: 'auto',
          marginBottom: '20px',
          padding: '20px',
          backgroundColor: 'white',
          borderRadius: '8px',
          boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
        }}
      >
        {messages.length === 0 ? (
          <p style={{ color: '#666', textAlign: 'center' }}>
            Start a conversation by typing a message below.
          </p>
        ) : (
          messages.map((msg, idx) => (
            <div
              key={idx}
              style={{
                marginBottom: '16px',
                padding: '12px',
                borderRadius: '8px',
                backgroundColor: msg.role === 'user' ? '#e3f2fd' : '#f5f5f5',
                textAlign: msg.role === 'user' ? 'right' : 'left',
              }}
            >
              <strong
                style={{
                  display: 'block',
                  marginBottom: '4px',
                  fontSize: '12px',
                  textTransform: 'uppercase',
                }}
              >
                {msg.role === 'user' ? 'You' : 'Assistant'}
              </strong>
              <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
              {msg.role === 'assistant' &&
                msg.citations &&
                msg.citations.length > 0 && (
                  <div style={{ marginTop: '12px', paddingTop: '12px', borderTop: '1px solid #ddd' }}>
                    <div
                      style={{
                        fontSize: '12px',
                        fontWeight: '600',
                        color: '#666',
                        marginBottom: '8px',
                      }}
                    >
                      Citations ({msg.citations.length})
                    </div>
                    {msg.citations.map((citation, cIdx) => (
                      <CitationItem key={cIdx} citation={citation} />
                    ))}
                  </div>
                )}
            </div>
          ))
        )}
        {loading && (
          <div
            style={{ textAlign: 'center', color: '#666', fontStyle: 'italic' }}
          >
            Thinking...
          </div>
        )}
      </div>

      <div style={{ display: 'flex', gap: '10px' }}>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder="Type your message..."
          disabled={loading}
          style={{
            flex: 1,
            padding: '12px',
            border: '1px solid #ddd',
            borderRadius: '4px',
            fontSize: '16px',
          }}
        />
        <button
          onClick={sendMessage}
          disabled={loading || !input.trim()}
          style={{
            padding: '12px 24px',
            backgroundColor: '#1976d2',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: loading || !input.trim() ? 'not-allowed' : 'pointer',
            fontSize: '16px',
            opacity: loading || !input.trim() ? 0.6 : 1,
          }}
        >
          Send
        </button>
      </div>
    </main>
  )
}
