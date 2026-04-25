package com.blog.ai.core.domain.post

import com.blog.ai.core.domain.rag.RagChunkService
import com.blog.ai.storage.post.BlogPostRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.OffsetDateTime

@Service
@Transactional(readOnly = true)
class BlogPostSyncService(
    private val blogPostRepository: BlogPostRepository,
    private val ragChunkService: RagChunkService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun upsert(command: SyncBlogPostCommand): SyncResult {
        val contentHash = hashContent(command.title, command.content)
        val affected =
            blogPostRepository.upsert(
                externalId = command.externalId,
                title = command.title,
                content = command.content,
                url = command.url,
                author = command.author,
                publishedAt = command.publishedAt,
                contentHash = contentHash,
                sourceUpdatedAt = command.sourceUpdatedAt,
                eventId = command.eventId,
            )
        return if (affected == 0) {
            log.info { "Upsert ignored (stale): externalId=${command.externalId}" }
            SyncResult.STALE_IGNORED
        } else {
            log.info { "Upsert applied: externalId=${command.externalId}" }
            SyncResult.APPLIED
        }
    }

    @Transactional
    fun softDelete(
        externalId: String,
        sourceUpdatedAt: OffsetDateTime,
        eventId: String?,
    ): SyncResult {
        val affected =
            blogPostRepository.softDelete(
                externalId = externalId,
                sourceUpdatedAt = sourceUpdatedAt,
                eventId = eventId,
            )
        return if (affected == 0) {
            log.info { "Delete ignored (stale): externalId=$externalId" }
            SyncResult.STALE_IGNORED
        } else {
            blogPostRepository.findByExternalId(externalId)?.id?.let(ragChunkService::deleteAuthorPost)
            log.info { "Delete tombstoned: externalId=$externalId" }
            SyncResult.TOMBSTONED
        }
    }

    private fun hashContent(
        title: String,
        content: String?,
    ): String {
        val source = "$title\n${content ?: ""}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
