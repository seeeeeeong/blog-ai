UPDATE articles
SET search_vector =
    setweight(to_tsvector('simple', korean_bigrams(title)), 'A') ||
    setweight(to_tsvector('simple', korean_bigrams(COALESCE(content, ''))), 'B')
WHERE search_vector IS NULL
  AND embedding IS NOT NULL;
