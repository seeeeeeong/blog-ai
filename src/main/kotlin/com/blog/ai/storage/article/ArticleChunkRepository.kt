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
            "INSERT INTO article_chunks (article_id, chunk_index, content) VALUES (?, ?, ?)",
            command.articleId,
            command.chunkIndex,
            command.content,
        )
    }

    fun deleteByArticleId(articleId: Long) {
        jdbcTemplate.update("DELETE FROM article_chunks WHERE article_id = ?", articleId)
    }
}
