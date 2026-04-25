package com.blog.ai.core.domain.chat

import com.blog.ai.storage.article.ArticleEntity
import com.blog.ai.storage.article.ArticleRepository
import com.blog.ai.storage.blog.BlogEntity
import com.blog.ai.storage.blog.BlogRepository
import com.blog.ai.support.PostgresTestContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.rag.Query
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest
@Import(PostgresTestContainer::class)
class BlogArticleDocumentRetrieverIntegrationTest
    @Autowired
    constructor(
        private val retriever: BlogArticleDocumentRetriever,
        private val articleRepository: ArticleRepository,
        private val blogRepository: BlogRepository,
        private val jdbcTemplate: JdbcTemplate,
        private val transactionTemplate: TransactionTemplate,
    ) {
        @MockitoBean
        private lateinit var embeddingModel: EmbeddingModel

        @MockitoBean
        private lateinit var chatQueryRewriter: ChatQueryRewriter

        @MockitoBean
        private lateinit var jinaRerankClient: JinaRerankClient

        @BeforeEach
        fun reset() {
            jdbcTemplate.update("TRUNCATE TABLE article_chunks RESTART IDENTITY")
            jdbcTemplate.update("TRUNCATE TABLE blog_post_chunks RESTART IDENTITY")
            jdbcTemplate.update("TRUNCATE TABLE articles RESTART IDENTITY CASCADE")
            jdbcTemplate.update("TRUNCATE TABLE blogs RESTART IDENTITY CASCADE")
            jdbcTemplate.update("TRUNCATE TABLE blog_posts RESTART IDENTITY CASCADE")

            Mockito.`when`(embeddingModel.embed(anyString())).thenReturn(FloatArray(1536) { 0.1f })
            Mockito.`when`(chatQueryRewriter.rewrite(anyString(), anyString())).thenAnswer { inv ->
                inv.getArgument<String>(1)
            }
            Mockito.`when`(jinaRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val docs = inv.getArgument<List<org.springframework.ai.document.Document>>(1)
                val topN = inv.getArgument<Int>(2)
                docs.take(topN)
            }
        }

        @Test
        fun `routes to author posts when author chunks match above threshold`() {
            seedAuthorPostWithChunk(
                externalId = "author-1",
                title = "My Kotlin journey",
                url = "https://author.example/k",
                chunkContent = "Kotlin coroutines are great",
                chunkVector = vector(0.1f),
            )
            val docs = retriever.retrieve(Query.builder().text("Kotlin coroutines").build())

            assertTrue(docs.isNotEmpty(), "expected at least one author doc")
            val author = docs.firstOrNull { it.metadata["sourceType"] == "author" }
            assertNotNull(author, "author doc should be present")
            assertEquals("My Kotlin journey", author!!.metadata["title"])
            assertEquals("https://author.example/k", author.metadata["url"])
        }

        @Test
        fun `falls back to external chunks when no author posts match`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Postgres deep dive",
                content = "vector search with pgvector",
                chunkContent = "pgvector HNSW indexing",
                chunkVector = vector(0.1f),
            )

            val docs = retriever.retrieve(Query.builder().text("pgvector").build())

            assertTrue(docs.isNotEmpty(), "expected at least one external doc")
            assertTrue(docs.all { it.metadata["sourceType"] == "external" })
        }

        @Test
        fun `returns empty list when no articles or author posts seeded`() {
            val docs = retriever.retrieve(Query.builder().text("anything").build())
            assertTrue(docs.isEmpty())
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

        private fun seedArticleWithChunk(
            blog: BlogEntity,
            title: String,
            content: String,
            chunkContent: String,
            chunkVector: String,
        ) {
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
            val articleId = requireNotNull(saved.id)
            transactionTemplate.execute {
                articleRepository.updateEmbedding(articleId, chunkVector, title, content)
            }
            jdbcTemplate.update(
                """
                INSERT INTO article_chunks (article_id, chunk_index, content, embedding)
                VALUES (?, ?, ?, CAST(? AS vector))
                """.trimIndent(),
                articleId,
                0,
                chunkContent,
                chunkVector,
            )
        }

        private fun seedAuthorPostWithChunk(
            externalId: String,
            title: String,
            url: String,
            chunkContent: String,
            chunkVector: String,
        ) {
            jdbcTemplate.update(
                """
                INSERT INTO blog_posts (
                    external_id, title, content, url, source_updated_at, synced_at,
                    is_deleted, embedding, content_hash, embed_retry_count
                ) VALUES (?, ?, ?, ?, NOW(), NOW(), false, CAST(? AS vector), ?, 0)
                """.trimIndent(),
                externalId,
                title,
                "body",
                url,
                chunkVector,
                "hash-$externalId",
            )
            val postId =
                jdbcTemplate.queryForObject(
                    "SELECT id FROM blog_posts WHERE external_id = ?",
                    Long::class.java,
                    externalId,
                )
            jdbcTemplate.update(
                """
                INSERT INTO blog_post_chunks (post_id, chunk_index, content, embedding)
                VALUES (?, ?, ?, CAST(? AS vector))
                """.trimIndent(),
                postId,
                0,
                chunkContent,
                chunkVector,
            )
        }

        private fun vector(value: Float): String = FloatArray(1536) { value }.joinToString(",", "[", "]")
    }
