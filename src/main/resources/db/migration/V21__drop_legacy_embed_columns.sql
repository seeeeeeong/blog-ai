ALTER TABLE articles ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMPTZ;
ALTER TABLE blog_posts ADD COLUMN IF NOT EXISTS embedded_at TIMESTAMPTZ;

UPDATE articles a
SET embedded_at = COALESCE(a.published_at, a.crawled_at)
WHERE a.embedded_at IS NULL
  AND EXISTS (
      SELECT 1 FROM rag_chunks r
      WHERE r.source_type = 'EXTERNAL_ARTICLE' AND r.source_id = a.id
  );

UPDATE blog_posts p
SET embedded_at = p.synced_at
WHERE p.embedded_at IS NULL
  AND EXISTS (
      SELECT 1 FROM rag_chunks r
      WHERE r.source_type = 'AUTHOR_POST' AND r.source_id = p.id
  );

DROP TABLE IF EXISTS article_chunks;
DROP TABLE IF EXISTS blog_post_chunks;

DROP INDEX IF EXISTS idx_articles_embed_null;
DROP INDEX IF EXISTS idx_articles_embedding;
DROP INDEX IF EXISTS idx_articles_search_vector;
ALTER TABLE articles DROP COLUMN IF EXISTS embedding;
ALTER TABLE articles DROP COLUMN IF EXISTS search_vector;

DROP INDEX IF EXISTS idx_blog_posts_embed_null;
DROP INDEX IF EXISTS idx_blog_posts_embedding;
DROP INDEX IF EXISTS idx_blog_posts_search_vector;
ALTER TABLE blog_posts DROP COLUMN IF EXISTS embedding;
ALTER TABLE blog_posts DROP COLUMN IF EXISTS search_vector;

CREATE INDEX IF NOT EXISTS idx_articles_embed_null
    ON articles(id)
    WHERE embedded_at IS NULL AND embed_error IS NULL;

CREATE INDEX IF NOT EXISTS idx_blog_posts_embed_null
    ON blog_posts(id)
    WHERE embedded_at IS NULL AND embed_error IS NULL AND is_deleted = false;
