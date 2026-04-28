package com.blog.ai.article.embedding

import com.blog.ai.article.ArticleEntity
import com.blog.ai.article.ArticleRepository
import com.blog.ai.blog.BlogEntity
import com.blog.ai.blog.BlogRepository
import com.blog.ai.support.PostgresTestContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest
@Import(PostgresTestContainer::class)
class ArticleEmbeddingServiceIntegrationTest
    @Autowired
    constructor(
        private val embedService: ArticleEmbeddingService,
        private val articleRepository: ArticleRepository,
        private val blogRepository: BlogRepository,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @MockitoBean
        private lateinit var embeddingModel: EmbeddingModel

        @BeforeEach
        fun reset() {
            jdbcTemplate.update("TRUNCATE TABLE rag_chunks RESTART IDENTITY")
            jdbcTemplate.update("TRUNCATE TABLE articles RESTART IDENTITY CASCADE")
            jdbcTemplate.update("TRUNCATE TABLE blogs RESTART IDENTITY CASCADE")
            Mockito.`when`(embeddingModel.embed(anyString())).thenReturn(FloatArray(1536) { 0.1f })
            Mockito.`when`(embeddingModel.embed(anyList<String>())).thenAnswer { inv ->
                val texts = inv.getArgument<List<String>>(0)
                texts.map { FloatArray(1536) { 0.1f } }
            }
        }

        @Test
        fun `embedPending maps TIMESTAMPTZ snapshot and writes vector plus chunks`() {
            val blog = seedBlog()
            val saved =
                articleRepository.save(
                    ArticleEntity.create(
                        blog = blog,
                        title = "Kotlin coroutines deep dive",
                        url = "https://example.com/a1",
                        urlHash = "hash-a1",
                        content = "Coroutines are lightweight threads. ".repeat(40),
                        publishedAt = OffsetDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                    ),
                )

            val processed = embedService.embedPending()

            assertEquals(1, processed)
            val articleId = requireNotNull(saved.id)
            assertTrue(isEmbedded(articleId), "article row should be marked embedded")
            assertNull(loadEmbedError(articleId), "embed_error should stay null on success")
            assertTrue(hasRagChunks(articleId), "rag chunks should be persisted for the article")
        }

        private fun seedBlog(): BlogEntity =
            blogRepository.save(
                BlogEntity.create(
                    name = "Example Eng",
                    company = "Example",
                    rssUrl = "https://example.com/rss",
                    homeUrl = "https://example.com",
                ),
            )

        private fun isEmbedded(articleId: Long): Boolean =
            jdbcTemplate.queryForObject(
                "SELECT embedded_at IS NOT NULL FROM articles WHERE id = ?",
                Boolean::class.java,
                articleId,
            ) ?: false

        private fun loadEmbedError(articleId: Long): String? =
            jdbcTemplate.queryForObject(
                "SELECT embed_error FROM articles WHERE id = ?",
                String::class.java,
                articleId,
            )

        private fun hasRagChunks(articleId: Long): Boolean =
            jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM rag_chunks
                    WHERE source_type = 'EXTERNAL_ARTICLE' AND source_id = ?
                )
                """.trimIndent(),
                Boolean::class.java,
                articleId,
            ) ?: false
    }
