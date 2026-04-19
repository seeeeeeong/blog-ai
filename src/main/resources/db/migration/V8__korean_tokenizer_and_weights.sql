CREATE OR REPLACE FUNCTION korean_bigrams(input text) RETURNS text
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
DECLARE
    result text := '';
    token  text;
    i      int;
    n      int;
BEGIN
    IF input IS NULL OR input = '' THEN
        RETURN '';
    END IF;

    FOR token IN
        SELECT unnest(regexp_split_to_array(lower(input), '[^[:alnum:]가-힣]+'))
    LOOP
        IF token IS NULL OR token = '' THEN
            CONTINUE;
        END IF;

        IF token ~ '^[a-z0-9_]+$' THEN
            result := result || ' ' || token;
            CONTINUE;
        END IF;

        n := char_length(token);
        IF n = 1 THEN
            result := result || ' ' || token;
        ELSE
            FOR i IN 1..(n - 1) LOOP
                result := result || ' ' || substring(token FROM i FOR 2);
            END LOOP;
        END IF;
    END LOOP;

    RETURN trim(result);
END;
$$;

UPDATE articles
SET search_vector =
    setweight(to_tsvector('simple', korean_bigrams(title)), 'A') ||
    setweight(to_tsvector('simple', korean_bigrams(COALESCE(content, ''))), 'B')
WHERE search_vector IS NOT NULL;

UPDATE blog_posts
SET search_vector =
    setweight(to_tsvector('simple', korean_bigrams(title)), 'A') ||
    setweight(to_tsvector('simple', korean_bigrams(COALESCE(content, ''))), 'B')
WHERE search_vector IS NOT NULL;
