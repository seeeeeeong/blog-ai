package com.blog.ai.post.service

import com.blog.ai.post.model.SyncPost
import com.blog.ai.post.model.SyncResult
import com.blog.ai.post.repository.PostRepository
import com.blog.ai.support.PostgresTestContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringBootTest
@Import(PostgresTestContainer::class)
class PostSyncEmbedIntegrationTest
    @Autowired
    constructor(
        private val syncService: PostSyncService,
        private val embedService: PostEmbeddingService,
        private val repository: PostRepository,
        private val jdbcTemplate: JdbcTemplate,
        private val transactionTemplate: TransactionTemplate,
    ) {
        @MockitoBean
        private lateinit var embeddingModel: EmbeddingModel

        @BeforeEach
        fun reset() {
            jdbcTemplate.update("TRUNCATE TABLE rag_chunks RESTART IDENTITY")
            jdbcTemplate.update("TRUNCATE TABLE blog_posts RESTART IDENTITY CASCADE")
            Mockito.`when`(embeddingModel.embed(anyString())).thenReturn(FloatArray(1536) { 0.1f })
            Mockito.`when`(embeddingModel.embed(anyList<String>())).thenAnswer { inv ->
                val texts = inv.getArgument<List<String>>(0)
                texts.map { FloatArray(1536) { 0.1f } }
            }
        }

        @Test
        fun `upsert applies newer event and ignores older event`() {
            val externalId = "post-1"
            val t1 = time(1)
            val t2 = time(2)

            assertEquals(SyncResult.APPLIED, syncService.upsert(command(externalId, "Old", "old body", t1, "e1")))
            assertEquals(SyncResult.APPLIED, syncService.upsert(command(externalId, "New", "new body", t2, "e2")))
            assertEquals(SyncResult.STALE_IGNORED, syncService.upsert(command(externalId, "Older", "older", t1, "e3")))

            val row = repository.findByExternalId(externalId)
            assertNotNull(row)
            assertEquals("New", row!!.title)
            assertEquals("new body", row.content)
            assertEquals("e2", row.lastEventId)
        }

        @Test
        fun `delete tombstones and ignores older upsert`() {
            val externalId = "post-2"
            val t1 = time(1)
            val t2 = time(2)
            val t3Stale = time(1, seconds = 30)

            syncService.upsert(command(externalId, "Alive", "body", t1, "u1"))
            assertEquals(SyncResult.TOMBSTONED, syncService.softDelete(externalId, t2, "d1"))
            assertEquals(
                SyncResult.STALE_IGNORED,
                syncService.upsert(command(externalId, "Revive", "body", t3Stale, "u2")),
            )

            val row = repository.findByExternalId(externalId)
            assertNotNull(row)
            assertTrue(row!!.isDeleted)
            assertEquals("d1", row.lastEventId)
        }

        @Test
        fun `content change resets embedding and is re-embedded on next tick`() {
            val externalId = "post-3"

            syncService.upsert(command(externalId, "T", "content A", time(1), "u1"))
            embedService.embedPending()

            val afterFirstEmbed = repository.findByExternalId(externalId)!!
            assertTrue(isEmbedded(afterFirstEmbed.id!!))
            val originalHash = afterFirstEmbed.contentHash

            syncService.upsert(command(externalId, "T", "content B", time(2), "u2"))

            val afterUpsert = repository.findByExternalId(externalId)!!
            assertFalse(isEmbedded(afterUpsert.id!!))
            assertFalse(afterUpsert.contentHash == originalHash)

            embedService.embedPending()
            assertTrue(isEmbedded(afterUpsert.id!!))
            assertTrue(hasRagChunks(afterUpsert.id!!), "rag chunks should be persisted for the post")
        }

        @Test
        fun `stale embed snapshot write is rejected by content_hash guard`() {
            val externalId = "post-4"

            syncService.upsert(command(externalId, "T", "initial", time(1), "u1"))
            val staleSnapshot = repository.findByExternalId(externalId)!!
            val staleHash = staleSnapshot.contentHash
            val staleId = staleSnapshot.id!!

            syncService.upsert(command(externalId, "T", "updated", time(2), "u2"))
            assertFalse(isEmbedded(staleId))

            val updated = inTx { repository.markEmbedded(staleId, staleHash) }
            assertEquals(0, updated)
            assertFalse(isEmbedded(staleId))

            val errorApplied = inTx { repository.updateEmbedError(staleId, "stale error", staleHash) }
            assertEquals(0, errorApplied)
            assertNull(repository.findByExternalId(externalId)!!.embedError)
        }

        @Test
        fun `embed write is rejected when row is tombstoned mid-flight`() {
            val externalId = "post-5"
            syncService.upsert(command(externalId, "T", "body", time(1), "u1"))
            val snapshot = repository.findByExternalId(externalId)!!

            syncService.softDelete(externalId, time(2), "d1")

            val updated = inTx { repository.markEmbedded(snapshot.id!!, snapshot.contentHash) }
            assertEquals(0, updated)
            assertFalse(isEmbedded(snapshot.id!!))
            assertTrue(repository.findByExternalId(externalId)!!.isDeleted)
        }

        private fun command(
            externalId: String,
            title: String,
            content: String?,
            sourceUpdatedAt: OffsetDateTime,
            eventId: String?,
        ) = SyncPost(
            externalId = externalId,
            title = title,
            content = content,
            url = "https://example.com/$externalId",
            author = "author",
            publishedAt = sourceUpdatedAt,
            sourceUpdatedAt = sourceUpdatedAt,
            eventId = eventId,
        )

        private fun time(
            minutes: Int,
            seconds: Int = 0,
        ): OffsetDateTime = OffsetDateTime.of(2026, 4, 1, 10, minutes, seconds, 0, ZoneOffset.UTC)

        private fun isEmbedded(postId: Long): Boolean =
            jdbcTemplate.queryForObject(
                "SELECT embedded_at IS NOT NULL FROM blog_posts WHERE id = ?",
                Boolean::class.java,
                postId,
            ) ?: false

        private fun hasRagChunks(postId: Long): Boolean =
            jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM rag_chunks
                    WHERE source_type = 'AUTHOR_POST' AND source_id = ?
                )
                """.trimIndent(),
                Boolean::class.java,
                postId,
            ) ?: false

        private fun <T : Any> inTx(block: () -> T): T = requireNotNull(transactionTemplate.execute { block() })
    }
