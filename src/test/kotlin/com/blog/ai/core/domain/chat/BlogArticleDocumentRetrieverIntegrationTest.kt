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
    ) {
        @MockitoBean
        private lateinit var embeddingModel: EmbeddingModel

        @MockitoBean
        private lateinit var chatQueryExpander: ChatQueryExpander

        @MockitoBean
        private lateinit var jinaRerankClient: JinaRerankClient

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
            Mockito.`when`(jinaRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
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
            assertTrue(
                docs.all { it.text.orEmpty().startsWith("External source (NOT Author post):") },
                "external docs must be explicitly marked as not author posts",
            )
        }

        @Test
        fun `returns empty list when no articles or author posts seeded`() {
            val docs = retriever.retrieve(Query.builder().text("anything").build())
            assertTrue(docs.isEmpty())
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
                    .context(mapOf(ChatService.REWRITTEN_QUERY_PARAM to "RAG 기반 관련 게시글 추천 시스템 설계"))
                    .build(),
            )

            assertEquals(listOf("RAG 기반 관련 게시글 추천 시스템 설계"), capturedExpansionInputs)
        }

        @Test
        fun `golden — abstains when rerank is unavailable so the call is fail-closed`() {
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Some article",
                content = "some content",
                chunkContent = "some content",
                chunkVector = vector(0.1f),
            )
            Mockito.`when`(jinaRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
                @Suppress("UNCHECKED_CAST")
                val docs = inv.getArgument<List<org.springframework.ai.document.Document>>(1)
                val topN = inv.getArgument<Int>(2)
                docs.take(topN)
            }

            val docs = retriever.retrieve(Query.builder().text("anything").build())

            assertTrue(docs.isEmpty(), "rerank without scores must trigger fail-closed abstain, not fall through")
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
            Mockito.`when`(jinaRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
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

            val docs = retriever.retrieve(Query.builder().text("RAG recommendation").build())

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
            Mockito.`when`(jinaRerankClient.rerank(anyString(), anyList(), anyInt())).thenAnswer { inv ->
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

            val docs = retriever.retrieve(Query.builder().text("RAG recommendation").build())

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

            val docs = retriever.retrieve(Query.builder().text("RAG 추천 시스템").build())

            assertEquals(1, docs.size, "duplicate hits across variants must be merged into one document")
            assertEquals("DoorDash retrieval", docs.first().metadata["title"])
        }

        @Test
        fun `golden — author posts retrieved alongside reranked supplementary externals`() {
            seedAuthorPostWithChunk(
                externalId = "author-mixed",
                title = "How I built RAG",
                url = "https://author.example/rag",
                chunkContent = "I built a RAG pipeline",
                chunkVector = vector(0.1f),
            )
            val blog = seedBlog()
            seedArticleWithChunk(
                blog = blog,
                title = "Industry RAG patterns",
                content = "patterns for production RAG",
                chunkContent = "patterns for production RAG",
                chunkVector = vector(0.1f),
            )

            val docs = retriever.retrieve(Query.builder().text("RAG").build())

            val author = docs.firstOrNull { it.metadata["sourceType"] == "author" }
            val external = docs.firstOrNull { it.metadata["sourceType"] == "external" }
            assertNotNull(author, "author doc should be present when author chunks match")
            assertNotNull(external, "external supplementary doc should be present")
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
