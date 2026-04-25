package com.blog.ai.core.domain.post

import com.blog.ai.storage.article.ArticleEntity
import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.storage.blog.BlogEntity
import com.blog.ai.storage.blog.BlogRepository
import com.blog.ai.support.PostgresTestContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest
@Import(PostgresTestContainer::class)
class BlogPostSimilarServiceIntegrationTest
    @Autowired
    constructor(
        private val similarService: BlogPostSimilarService,
        private val articleRepository: ArticleRepository,
        private val blogRepository: BlogRepository,
        private val jdbcTemplate: JdbcTemplate,
        private val transactionTemplate: TransactionTemplate,
    ) {
        @BeforeEach
        fun reset() {
            jdbcTemplate.update("TRUNCATE TABLE article_chunks RESTART IDENTITY")
            jdbcTemplate.update("TRUNCATE TABLE articles RESTART IDENTITY CASCADE")
            jdbcTemplate.update("TRUNCATE TABLE blogs RESTART IDENTITY CASCADE")
            jdbcTemplate.update("TRUNCATE TABLE blog_posts RESTART IDENTITY CASCADE")
        }

        @Test
        fun `returns NOT_FOUND when externalId is unknown`() {
            val result = similarService.findSimilar("missing")
            assertEquals(SimilarStatus.NOT_FOUND, result.status)
            assertTrue(result.items.isEmpty())
        }

        @Test
        fun `returns DELETED when post is tombstoned`() {
            seedBlogPost(externalId = "post-deleted", isDeleted = true, embedding = vector(0.1f))

            val result = similarService.findSimilar("post-deleted")
            assertEquals(SimilarStatus.DELETED, result.status)
        }

        @Test
        fun `returns PENDING when post has no embedding yet`() {
            seedBlogPost(externalId = "post-pending", embedding = null)

            val result = similarService.findSimilar("post-pending")
            assertEquals(SimilarStatus.PENDING, result.status)
        }

        @Test
        fun `returns READY hits ranked by hybrid RRF score`() {
            val blog = seedBlog()
            val close =
                seedArticle(
                    blog = blog,
                    title = "Kotlin coroutines deep dive",
                    content = "Coroutines structured concurrency in Kotlin",
                    embedding = vector(0.1f),
                )
            val far =
                seedArticle(
                    blog = blog,
                    title = "Unrelated cooking recipe",
                    content = "How to bake bread",
                    embedding = vector(0.9f),
                )
            seedBlogPost(
                externalId = "post-ready",
                title = "Kotlin coroutines",
                content = "About coroutines and structured concurrency",
                embedding = vector(0.1f),
            )

            val result = similarService.findSimilar("post-ready", limit = 5)

            assertEquals(SimilarStatus.READY, result.status)
            assertTrue(result.items.isNotEmpty(), "expected at least one similar article")
            val ids = result.items.map { it.id }
            assertTrue(close in ids, "close article should appear in results")
            if (far in ids) {
                val closeRank = ids.indexOf(close)
                val farRank = ids.indexOf(far)
                assertTrue(closeRank < farRank, "close article should rank above far article")
            }
            assertNotEquals(0.0, result.items.first().score)
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

        private fun seedArticle(
            blog: BlogEntity,
            title: String,
            content: String,
            embedding: String,
        ): Long {
            val saved =
                articleRepository.save(
                    ArticleEntity.create(
                        blog = blog,
                        title = title,
                        url = "https://example.com/${title.hashCode()}",
                        urlHash = "hash-${title.hashCode()}",
                        content = content,
                        publishedAt = OffsetDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                    ),
                )
            val id = requireNotNull(saved.id)
            transactionTemplate.execute { articleRepository.updateEmbedding(id, embedding, title, content) }
            return id
        }

        private fun seedBlogPost(
            externalId: String,
            title: String = "Title",
            content: String? = "content",
            embedding: String? = vector(0.1f),
            isDeleted: Boolean = false,
        ) {
            jdbcTemplate.update(
                """
                INSERT INTO blog_posts (
                    external_id, title, content, source_updated_at, synced_at,
                    is_deleted, embedding, content_hash, embed_retry_count
                ) VALUES (?, ?, ?, NOW(), NOW(), ?, CAST(? AS vector), ?, 0)
                """.trimIndent(),
                externalId,
                title,
                content,
                isDeleted,
                embedding,
                "hash-$externalId",
            )
        }

        private fun vector(value: Float): String = FloatArray(1536) { value }.joinToString(",", "[", "]")
    }
