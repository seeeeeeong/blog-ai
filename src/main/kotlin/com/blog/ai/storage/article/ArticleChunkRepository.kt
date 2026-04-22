package com.blog.ai.storage.article

import com.blog.ai.core.domain.article.SaveChunkCommand
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ArticleChunkRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun saveChunk(command: SaveChunkCommand) {
        jdbcTemplate.update(
            """
            INSERT INTO article_chunks (article_id, chunk_index, content, embedding)
            VALUES (?, ?, ?, CAST(? AS vector))
            """.trimIndent(),
            command.articleId,
            command.chunkIndex,
            command.content,
            command.embedding,
        )
    }

    fun deleteByArticleId(articleId: Long) {
        jdbcTemplate.update("DELETE FROM article_chunks WHERE article_id = ?", articleId)
    }

    fun truncateAll() {
        jdbcTemplate.update("TRUNCATE TABLE article_chunks RESTART IDENTITY")
    }

    fun findChunkIndicesByArticleId(articleId: Long): List<Int> =
        jdbcTemplate.query(
            "SELECT chunk_index FROM article_chunks WHERE article_id = ? ORDER BY chunk_index",
            { rs, _ -> rs.getInt("chunk_index") },
            articleId,
        )

    fun findSimilarChunks(
        queryVector: String,
        topK: Int,
        similarityThreshold: Double,
    ): List<ArticleChunkHit> =
        jdbcTemplate.query(
            """
            SELECT
                c.article_id,
                c.chunk_index,
                c.content,
                a.title,
                a.url,
                b.company,
                1 - (c.embedding <=> CAST(? AS vector)) AS similarity
            FROM article_chunks c
            JOIN articles a ON a.id = c.article_id
            JOIN blogs    b ON b.id = a.blog_id
            WHERE c.embedding IS NOT NULL
              AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
            ORDER BY c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                ArticleChunkHit(
                    articleId = rs.getLong("article_id"),
                    chunkIndex = rs.getInt("chunk_index"),
                    content = rs.getString("content"),
                    title = rs.getString("title"),
                    url = rs.getString("url"),
                    company = rs.getString("company"),
                    similarity = rs.getDouble("similarity"),
                )
            },
            queryVector,
            queryVector,
            similarityThreshold,
            queryVector,
            topK,
        )
}

data class ArticleChunkHit(
    val articleId: Long,
    val chunkIndex: Int,
    val content: String,
    val title: String,
    val url: String,
    val company: String,
    val similarity: Double,
)
