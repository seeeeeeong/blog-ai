CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE blogs (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    company    VARCHAR(100) NOT NULL,
    rss_url    VARCHAR(500) NOT NULL,
    home_url   VARCHAR(500) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE articles (
    id            BIGSERIAL PRIMARY KEY,
    blog_id       BIGINT NOT NULL REFERENCES blogs(id),
    title         TEXT NOT NULL,
    url           TEXT NOT NULL,
    url_hash      VARCHAR(64) NOT NULL,
    content       TEXT,
    published_at  TIMESTAMPTZ,
    crawled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    embedding     VECTOR(1536),
    embed_error   TEXT,
    search_vector TSVECTOR
);

CREATE UNIQUE INDEX idx_articles_url_hash ON articles(url_hash);
CREATE INDEX idx_articles_blog_id ON articles(blog_id);
CREATE INDEX idx_articles_embed_null ON articles(id) WHERE embedding IS NULL AND embed_error IS NULL;
CREATE INDEX idx_articles_embedding ON articles USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_articles_search_vector ON articles USING GIN (search_vector);

CREATE TABLE article_chunks (
    id          BIGSERIAL PRIMARY KEY,
    article_id  BIGINT NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content     TEXT NOT NULL,
    embedding   VECTOR(1536)
);

CREATE INDEX idx_article_chunks_article_id ON article_chunks(article_id);
CREATE INDEX idx_article_chunks_embedding ON article_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE hn_trending (
    id         INT PRIMARY KEY DEFAULT 1,
    items      JSONB,
    fetched_at TIMESTAMPTZ DEFAULT now()
);

-- Initial blog data
INSERT INTO blogs (name, company, rss_url, home_url) VALUES
    ('toss tech blog',           '토스',         'https://toss.tech/rss.xml',                   'https://toss.tech'),
    ('우아한형제들 기술블로그',     '우아한형제들',   'https://techblog.woowahan.com/feed/',         'https://techblog.woowahan.com'),
    ('카카오 기술블로그',          '카카오',        'https://tech.kakao.com/feed/',                'https://tech.kakao.com'),
    ('NAVER D2',                 '네이버',        'https://d2.naver.com/d2.atom',                'https://d2.naver.com'),
    ('Coupang Engineering',      '쿠팡',          'https://medium.com/feed/coupang-engineering', 'https://medium.com/coupang-engineering'),
    ('당근 팀 블로그',             '당근',          'https://medium.com/feed/daangn',             'https://medium.com/daangn'),
    ('LINE Engineering',         '라인',          'https://engineering.linecorp.com/ko/feed',    'https://engineering.linecorp.com/ko'),
    ('컬리 기술 블로그',           '컬리',          'https://helloworld.kurly.com/feed.xml',      'https://helloworld.kurly.com'),
    ('카카오페이 기술블로그',       '카카오페이',     'https://tech.kakaopay.com/rss',              'https://tech.kakaopay.com'),
    ('뱅크샐러드 기술블로그',       '뱅크샐러드',     'https://blog.banksalad.com/rss.xml',        'https://blog.banksalad.com');
