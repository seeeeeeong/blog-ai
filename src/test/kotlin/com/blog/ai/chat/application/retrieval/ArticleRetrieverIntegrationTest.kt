package com.blog.ai.chat.application.retrieval

import com.blog.ai.article.infrastructure.ArticleEntity
import com.blog.ai.article.infrastructure.ArticleRepository
import com.blog.ai.blog.infrastructure.BlogEntity
import com.blog.ai.blog.infrastructure.BlogRepository
import com.blog.ai.chat.application.retrieval.QueryExpander
import com.blog.ai.chat.domain.ChatAdvisorParams
import com.blog.ai.chat.domain.ChatMode
import com.blog.ai.chat.infrastructure.rerank.RerankClient
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
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest
@Import(PostgresTestContainer::class)
class ArticleRetrieverIntegrationTest
    @Autowired
    constructor(
        private val retriever: ArticleRetriever,
        private val articleRepository: ArticleRepository,
        private val blogRepository: BlogRepository,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @MockitoBean
        private lateinit var embeddingModel: EmbeddingModel

        @MockitoBean
        private lateinit var chatQueryExpander: QueryExpander

        @MockitoBean
        private lateinit var chatRerankClient: RerankClient

        @BeforeEach
        fun reset() {
            jdbcTemplate.update("TRUNCATE TABLE rag_chunks RESTART IDENTITY")
            jdbcTemplate.update("TRUNCATE TABLE articles RESTART IDENTITY CASCADE")
            jdbcTemplate.update("TRUNCATE TABLE blogs RESTART IDENTITY CASCADE")
            jdbcTemplate.update("TRUNCATE TABLE blog_posts RESTART IDENTITY CASCADE")

            Mockito.`when`(embeddingModel.embed(anyString())).thenReturn(FloatArray(1536) { 0.1f })
            Mockito.`when`(chatQueryExpander.expand(anyString())).thenAnswer { inv ->
                listOf(inv.getArgument<String>(0))
            }
            Mockito.`when`(chatRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val docs = inv.getArgument<List<org.springframework.ai.document.Document>>(1)
                val topN = inv.getArgument<Int>(2)
                docs.take(topN).map { d ->
                    org.springframework.ai.document.Document(
                        d.id,
                        d.text.orEmpty(),
                        d.metadata + ("rerankScore" to 0.9),
                    )
                }
            }
        }

        @Test
        fun `AUTHOR_POST mode returns only author docs`() {
            seedAuthorPostWithChunk(
                externalId = "author-1",
                title = "My Kotlin journey",
                url = "https://author.example/k",
                chunkContent = "Kotlin coroutines are great",
                chunkVector = vector(0.1f),
            )
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Industry Kotlin patterns",
                content = "patterns",
                chunkContent = "patterns",
                chunkVector = vector(0.1f),
            )

            val docs = retriever.retrieve(authorQuery("Kotlin coroutines"))

            assertTrue(docs.isNotEmpty(), "expected at least one author doc")
            assertTrue(
                docs.all { it.metadata["sourceType"] == "author" },
                "AUTHOR_POST mode must not surface external supplementary docs",
            )
            val author = docs.first()
            assertEquals("My Kotlin journey", author.metadata["title"])
            assertEquals("https://author.example/k", author.metadata["url"])
        }

        @Test
        fun `AUTHOR_POST mode returns empty when only external articles are seeded`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Postgres deep dive",
                content = "vector search with pgvector",
                chunkContent = "pgvector HNSW indexing",
                chunkVector = vector(0.1f),
            )

            val docs = retriever.retrieve(authorQuery("pgvector"))

            assertTrue(docs.isEmpty(), "AUTHOR_POST mode must not fall back to external articles")
        }

        @Test
        fun `EXTERNAL_ARTICLE mode returns only external docs`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Postgres deep dive",
                content = "vector search with pgvector",
                chunkContent = "pgvector HNSW indexing",
                chunkVector = vector(0.1f),
            )
            seedAuthorPostWithChunk(
                externalId = "author-mixed",
                title = "How I built RAG",
                url = "https://author.example/rag",
                chunkContent = "I built a RAG pipeline using pgvector",
                chunkVector = vector(0.1f),
            )

            val docs = retriever.retrieve(externalQuery("pgvector"))

            assertTrue(docs.isNotEmpty(), "expected at least one external doc")
            assertTrue(
                docs.all { it.metadata["sourceType"] == "external" },
                "EXTERNAL_ARTICLE mode must not surface author posts",
            )
            assertTrue(
                docs.all { it.text.orEmpty().startsWith("External source (NOT Author post):") },
                "external docs must be explicitly marked as not author posts",
            )
        }

        @Test
        fun `EXTERNAL_ARTICLE mode returns empty when only author posts are seeded`() {
            seedAuthorPostWithChunk(
                externalId = "author-1",
                title = "My RAG journey",
                url = "https://author.example/r",
                chunkContent = "I built a RAG pipeline",
                chunkVector = vector(0.1f),
            )

            val docs = retriever.retrieve(externalQuery("RAG"))

            assertTrue(docs.isEmpty(), "EXTERNAL_ARTICLE mode must not fall back to author posts")
        }

        @Test
        fun `returns empty list when no articles or author posts seeded`() {
            val docs = retriever.retrieve(externalQuery("anything"))
            assertTrue(docs.isEmpty())
        }

        @Test
        fun `default mode (no advisor param) is EXTERNAL_ARTICLE`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "External post",
                content = "external",
                chunkContent = "external",
                chunkVector = vector(0.1f),
            )

            val docs = retriever.retrieve(Query.builder().text("external").build())

            assertTrue(docs.isNotEmpty(), "missing MODE param should default to EXTERNAL_ARTICLE retrieval")
            assertTrue(docs.all { it.metadata["sourceType"] == "external" })
        }

        @Test
        fun `uses rewritten query from advisor context when provided`() {
            val capturedExpansionInputs = mutableListOf<String>()
            Mockito.`when`(chatQueryExpander.expand(anyString())).thenAnswer { inv ->
                val q = inv.getArgument<String>(0)
                capturedExpansionInputs += q
                listOf(q)
            }

            retriever.retrieve(
                Query
                    .builder()
                    .text("아니 관련 게시글 추천 이런거")
                    .context(
                        mapOf(
                            ChatAdvisorParams.REWRITTEN_QUERY to "RAG 기반 관련 게시글 추천 시스템 설계",
                            ChatAdvisorParams.MODE to ChatMode.EXTERNAL_ARTICLE.name,
                        ),
                    ).build(),
            )

            assertEquals(listOf("RAG 기반 관련 게시글 추천 시스템 설계"), capturedExpansionInputs)
        }

        @Test
        fun `golden — abstains when rerank returns docs without scores (key missing fallback)`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Some article",
                content = "some content",
                chunkContent = "some content",
                chunkVector = vector(0.1f),
            )
            Mockito.`when`(chatRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val docs = inv.getArgument<List<org.springframework.ai.document.Document>>(1)
                val topN = inv.getArgument<Int>(2)
                docs.take(topN)
            }

            val docs = retriever.retrieve(externalQuery("anything"))

            assertTrue(docs.isEmpty(), "rerank without scores must trigger fail-closed abstain, not fall through")
        }

        @Test
        fun `golden — abstains when rerank returns an empty list (Jina anomaly)`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Some article",
                content = "some content",
                chunkContent = "some content",
                chunkVector = vector(0.1f),
            )
            Mockito
                .`when`(chatRerankClient.rerank(anyString(), anyList(), anyInt()))
                .thenReturn(emptyList())

            val docs = retriever.retrieve(externalQuery("anything"))

            assertTrue(docs.isEmpty(), "rerank returning empty list must also be treated as unavailable")
        }

        @Test
        fun `golden — abstains when top rerank score is below abstain threshold even if above eligibility`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Marginally related article",
                content = "marginal content",
                chunkContent = "marginal content",
                chunkVector = vector(0.1f),
            )
            Mockito.`when`(chatRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val docs = inv.getArgument<List<org.springframework.ai.document.Document>>(1)
                val topN = inv.getArgument<Int>(2)
                docs.take(topN).map { d ->
                    org.springframework.ai.document.Document(
                        d.id,
                        d.text.orEmpty(),
                        d.metadata + ("rerankScore" to 0.45),
                    )
                }
            }

            val docs = retriever.retrieve(externalQuery("RAG recommendation"))

            assertTrue(docs.isEmpty(), "top score 0.45 sits above eligibility 0.4 but below abstain 0.5")
        }

        @Test
        fun `golden — rerank floor drops candidates whose relevance is below threshold`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Off-topic article",
                content = "completely unrelated content",
                chunkContent = "completely unrelated content",
                chunkVector = vector(0.1f),
            )
            Mockito.`when`(chatRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val docs = inv.getArgument<List<org.springframework.ai.document.Document>>(1)
                val topN = inv.getArgument<Int>(2)
                docs.take(topN).map { d ->
                    org.springframework.ai.document.Document(
                        d.id,
                        d.text.orEmpty(),
                        d.metadata + ("rerankScore" to 0.05),
                    )
                }
            }

            val docs = retriever.retrieve(externalQuery("RAG recommendation"))

            assertTrue(docs.isEmpty(), "all candidates below floor should yield empty result")
        }

        @Test
        fun `golden — query expansion unions hits across variants and dedupes`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "DoorDash retrieval",
                content = "DoorDash uses LLM-powered retrieval",
                chunkContent = "DoorDash uses LLM-powered retrieval",
                chunkVector = vector(0.1f),
            )
            Mockito
                .`when`(chatQueryExpander.expand(anyString()))
                .thenReturn(listOf("RAG 추천 시스템", "retrieval augmented recommendation"))

            val docs = retriever.retrieve(externalQuery("RAG 추천 시스템"))

            assertEquals(1, docs.size, "duplicate hits across variants must be merged into one document")
            assertEquals("DoorDash retrieval", docs.first().metadata["title"])
        }

        private fun authorQuery(text: String): Query =
            Query
                .builder()
                .text(text)
                .context(mapOf(ChatAdvisorParams.MODE to ChatMode.AUTHOR_POST.name))
                .build()

        private fun externalQuery(text: String): Query =
            Query
                .builder()
                .text(text)
                .context(mapOf(ChatAdvisorParams.MODE to ChatMode.EXTERNAL_ARTICLE.name))
                .build()

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
            jdbcTemplate.update(
                """
                INSERT INTO rag_chunks (
                    source_type, source_id, granularity, chunk_index,
                    title, url, company, content, embedding, search_vector
                )
                VALUES (
                    'EXTERNAL_ARTICLE', ?, 'CHUNK', ?, ?, ?, ?,
                    ?, CAST(? AS vector),
                    setweight(to_tsvector('simple', korean_bigrams(?)), 'A') ||
                        setweight(to_tsvector('simple', korean_bigrams(?)), 'B')
                )
                """.trimIndent(),
                articleId,
                0,
                title,
                "https://example.com/${title.hashCode()}",
                blog.company,
                chunkContent,
                chunkVector,
                title,
                chunkContent,
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
                    is_deleted, content_hash, embed_retry_count, embedded_at
                ) VALUES (?, ?, ?, ?, NOW(), NOW(), false, ?, 0, NOW())
                """.trimIndent(),
                externalId,
                title,
                "body",
                url,
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
                INSERT INTO rag_chunks (
                    source_type, source_id, granularity, chunk_index,
                    title, url, company, content, embedding, search_vector
                )
                VALUES (
                    'AUTHOR_POST', ?, 'CHUNK', ?, ?, ?, NULL,
                    ?, CAST(? AS vector),
                    setweight(to_tsvector('simple', korean_bigrams(?)), 'A') ||
                        setweight(to_tsvector('simple', korean_bigrams(?)), 'B')
                )
                """.trimIndent(),
                postId,
                0,
                title,
                url,
                chunkContent,
                chunkVector,
                title,
                chunkContent,
            )
        }

        private fun vector(value: Float): String = FloatArray(1536) { value }.joinToString(",", "[", "]")
    }
