-- Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Track “indexing targets” (codebases / doc sets)
CREATE TABLE IF NOT EXISTS repositories (
  id           BIGSERIAL PRIMARY KEY,
  name         TEXT NOT NULL,
  root_path    TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Each file/document in a repo
CREATE TABLE IF NOT EXISTS documents (
  id           BIGSERIAL PRIMARY KEY,
  repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
  file_path    TEXT NOT NULL,
  content_hash TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(repository_id, file_path)
);

-- Chunks of each document (what we will embed later)
-- Choose a default embedding dimension now; you can change later.
-- 1536 is common for many embedding models.
CREATE TABLE IF NOT EXISTS chunks (
  id           BIGSERIAL PRIMARY KEY,
  document_id  BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  chunk_index  INT NOT NULL,
  start_line   INT,
  end_line     INT,
  content      TEXT NOT NULL,
  content_hash TEXT,
  embedding    VECTOR(1536),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(document_id, chunk_index)
);

-- Optional: useful indexes for later (not required, but harmless)
CREATE INDEX IF NOT EXISTS idx_documents_repo ON documents(repository_id);
CREATE INDEX IF NOT EXISTS idx_chunks_document ON chunks(document_id);
