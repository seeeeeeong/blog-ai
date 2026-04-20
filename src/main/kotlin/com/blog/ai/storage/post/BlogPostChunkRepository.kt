package com.blog.ai.storage.post

import com.blog.ai.core.domain.post.SaveBlogPostChunkCommand
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class BlogPostChunkRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun saveChunk(command: SaveBlogPostChunkCommand) {
        jdbcTemplate.update(
            """
            INSERT INTO blog_post_chunks (post_id, chunk_index, content, embedding)
            VALUES (?, ?, ?, CAST(? AS vector))
            """.trimIndent(),
            command.postId,
            command.chunkIndex,
            command.content,
            command.embedding,
        )
    }

    fun deleteByPostId(postId: Long) {
        jdbcTemplate.update("DELETE FROM blog_post_chunks WHERE post_id = ?", postId)
    }

    fun findSimilarChunks(
        queryVector: String,
        topK: Int,
        similarityThreshold: Double,
    ): List<BlogPostChunkHit> =
        jdbcTemplate.query(
            """
            SELECT
                c.post_id,
                c.chunk_index,
                c.content,
                p.title,
                p.external_id,
                1 - (c.embedding <=> CAST(? AS vector)) AS similarity
            FROM blog_post_chunks c
            JOIN blog_posts p ON p.id = c.post_id
            WHERE p.is_deleted = false
              AND c.embedding IS NOT NULL
              AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
            ORDER BY c.embedding <=> CAST(? AS vector)
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                BlogPostChunkHit(
                    postId = rs.getLong("post_id"),
                    chunkIndex = rs.getInt("chunk_index"),
                    content = rs.getString("content"),
                    title = rs.getString("title"),
                    externalId = rs.getString("external_id"),
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

data class BlogPostChunkHit(
    val postId: Long,
    val chunkIndex: Int,
    val content: String,
    val title: String,
    val externalId: String,
    val similarity: Double,
)
