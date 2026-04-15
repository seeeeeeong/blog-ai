package com.blog.ai.storage.article

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ArticleChunkRepository(
    private val jdbcTemplate: JdbcTemplate,
) {

    fun saveChunk(articleId: Long, chunkIndex: Int, content: String) {
        jdbcTemplate.update(
            "INSERT INTO article_chunks (article_id, chunk_index, content) VALUES (?, ?, ?)",
            articleId, chunkIndex, content,
        )
    }

    fun deleteByArticleId(articleId: Long) {
        jdbcTemplate.update("DELETE FROM article_chunks WHERE article_id = ?", articleId)
    }
}
