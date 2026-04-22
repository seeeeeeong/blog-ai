-- article_chunks.embedding was never populated (SaveChunk does not write it).
-- Vector search for external article chunks uses Spring AI's managed vector_store table.
-- Drop the dead column and its HNSW index to simplify the schema.

DROP INDEX IF EXISTS idx_article_chunks_embedding;
ALTER TABLE article_chunks DROP COLUMN IF EXISTS embedding;
