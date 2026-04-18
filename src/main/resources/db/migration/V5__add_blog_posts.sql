CREATE TABLE IF NOT EXISTS blog_posts (
    id                BIGSERIAL PRIMARY KEY,
    external_id       VARCHAR(64) NOT NULL UNIQUE,
    title             TEXT NOT NULL,
    content           TEXT,
    url               TEXT,
    author            VARCHAR(100),
    published_at      TIMESTAMPTZ,
    content_hash      VARCHAR(64),
    embedding         VECTOR(1536),
    search_vector     TSVECTOR,
    embed_error       TEXT,
    embed_retry_count INT NOT NULL DEFAULT 0,
    source_updated_at TIMESTAMPTZ NOT NULL,
    last_event_id     VARCHAR(64),
    synced_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted        BOOLEAN NOT NULL DEFAULT false,
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_blog_posts_embed_null
    ON blog_posts(id)
    WHERE embedding IS NULL AND embed_error IS NULL AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_blog_posts_embedding
    ON blog_posts USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_blog_posts_search_vector
    ON blog_posts USING GIN (search_vector);
