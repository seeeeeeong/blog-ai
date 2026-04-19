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
}
