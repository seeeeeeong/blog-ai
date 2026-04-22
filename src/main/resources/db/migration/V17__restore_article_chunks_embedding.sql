-- Restore article_chunks.embedding column dropped in V14.
-- Article retrieval now writes chunk embeddings directly (no Spring AI VectorStore)
-- so chunk transactions stay DB-only and avoid external API calls inside DB txn.
ALTER TABLE article_chunks ADD COLUMN IF NOT EXISTS embedding VECTOR(1536);

CREATE INDEX IF NOT EXISTS idx_article_chunks_embedding
    ON article_chunks USING hnsw (embedding vector_cosine_ops);
