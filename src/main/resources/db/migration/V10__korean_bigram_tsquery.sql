CREATE OR REPLACE FUNCTION korean_bigram_tsquery(input text) RETURNS tsquery
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
DECLARE
    bigrams text;
BEGIN
    bigrams := korean_bigrams(input);
    IF bigrams IS NULL OR bigrams = '' THEN
        RETURN to_tsquery('simple', '');
    END IF;

    RETURN to_tsquery('simple', regexp_replace(trim(bigrams), '\s+', ' | ', 'g'));
END;
$$;
