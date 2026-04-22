-- Drop the Spring AI VectorStore-managed table. Article chunks now live in
-- article_chunks with their own embedding column (V17), BlogPost chunks have
-- their own table as well. No reader references vector_store.
DROP TABLE IF EXISTS vector_store;
