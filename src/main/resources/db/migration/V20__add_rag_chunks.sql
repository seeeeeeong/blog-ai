CREATE TABLE IF NOT EXISTS rag_chunks (
    id           BIGSERIAL PRIMARY KEY,
    source_type  VARCHAR(32) NOT NULL,
    source_id    BIGINT NOT NULL,
    granularity  VARCHAR(16) NOT NULL,
    chunk_index  INT NOT NULL,
    title        TEXT NOT NULL,
    url          TEXT,
    company      VARCHAR(100),
    content      TEXT NOT NULL,
    embedding    VECTOR(1536) NOT NULL,
    search_vector TSVECTOR,
    metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rag_chunks_source_type
        CHECK (source_type IN ('AUTHOR_POST', 'EXTERNAL_ARTICLE')),
    CONSTRAINT chk_rag_chunks_granularity
        CHECK (granularity IN ('DOCUMENT', 'CHUNK')),
    CONSTRAINT uk_rag_chunks_source
        UNIQUE (source_type, source_id, granularity, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_source_type
    ON rag_chunks(source_type);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding
    ON rag_chunks USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_search_vector
    ON rag_chunks USING GIN (search_vector);

INSERT INTO rag_chunks (
    source_type, source_id, granularity, chunk_index,
    title, url, company, content, embedding, search_vector, metadata
)
SELECT
    'AUTHOR_POST',
    p.id,
    'DOCUMENT',
    -1,
    p.title,
    p.url,
    NULL,
    COALESCE(p.content, ''),
    p.embedding,
    p.search_vector,
    jsonb_build_object('externalId', p.external_id, 'author', p.author)
FROM blog_posts p
WHERE p.is_deleted = false
  AND p.embedding IS NOT NULL
  AND COALESCE(p.content, '') <> ''
ON CONFLICT (source_type, source_id, granularity, chunk_index) DO NOTHING;

INSERT INTO rag_chunks (
    source_type, source_id, granularity, chunk_index,
    title, url, company, content, embedding, search_vector, metadata
)
SELECT
    'AUTHOR_POST',
    p.id,
    'CHUNK',
    c.chunk_index,
    p.title,
    p.url,
    NULL,
    c.content,
    c.embedding,
    setweight(to_tsvector('simple', korean_bigrams(p.title)), 'A') ||
        setweight(to_tsvector('simple', korean_bigrams(c.content)), 'B'),
    jsonb_build_object('externalId', p.external_id, 'author', p.author)
FROM blog_post_chunks c
JOIN blog_posts p ON p.id = c.post_id
WHERE p.is_deleted = false
  AND c.embedding IS NOT NULL
ON CONFLICT (source_type, source_id, granularity, chunk_index) DO NOTHING;

INSERT INTO rag_chunks (
    source_type, source_id, granularity, chunk_index,
    title, url, company, content, embedding, search_vector, metadata
)
SELECT
    'EXTERNAL_ARTICLE',
    a.id,
    'DOCUMENT',
    -1,
    a.title,
    a.url,
    b.company,
    COALESCE(a.content, ''),
    a.embedding,
    a.search_vector,
    jsonb_build_object('blogId', b.id, 'blogName', b.name)
FROM articles a
JOIN blogs b ON b.id = a.blog_id
WHERE a.embedding IS NOT NULL
  AND COALESCE(a.content, '') <> ''
ON CONFLICT (source_type, source_id, granularity, chunk_index) DO NOTHING;

INSERT INTO rag_chunks (
    source_type, source_id, granularity, chunk_index,
    title, url, company, content, embedding, search_vector, metadata
)
SELECT
    'EXTERNAL_ARTICLE',
    a.id,
    'CHUNK',
    c.chunk_index,
    a.title,
    a.url,
    b.company,
    c.content,
    c.embedding,
    setweight(to_tsvector('simple', korean_bigrams(a.title)), 'A') ||
        setweight(to_tsvector('simple', korean_bigrams(c.content)), 'B'),
    jsonb_build_object('blogId', b.id, 'blogName', b.name)
FROM article_chunks c
JOIN articles a ON a.id = c.article_id
JOIN blogs b ON b.id = a.blog_id
WHERE c.embedding IS NOT NULL
ON CONFLICT (source_type, source_id, granularity, chunk_index) DO NOTHING;
