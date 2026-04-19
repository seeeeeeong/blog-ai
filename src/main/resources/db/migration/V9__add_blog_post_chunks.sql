CREATE TABLE blog_post_chunks (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT NOT NULL REFERENCES blog_posts(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content     TEXT NOT NULL,
    embedding   VECTOR(1536)
);

CREATE INDEX idx_blog_post_chunks_post_id ON blog_post_chunks(post_id);
CREATE INDEX idx_blog_post_chunks_embedding
    ON blog_post_chunks USING hnsw (embedding vector_cosine_ops);
