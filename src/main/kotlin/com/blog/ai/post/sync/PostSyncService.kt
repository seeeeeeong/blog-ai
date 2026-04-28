package com.blog.ai.post.sync

import com.blog.ai.post.PostRepository
import com.blog.ai.post.sync.SyncPost
import com.blog.ai.post.sync.SyncResult
import com.blog.ai.rag.RagWriteService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.OffsetDateTime

@Service
@Transactional(readOnly = true)
class PostSyncService(
    private val postRepository: PostRepository,
    private val ragWriteService: RagWriteService,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional
    fun upsert(command: SyncPost): SyncResult {
        val contentHash = hashContent(command.title, command.content)
        val affected =
            postRepository.upsert(
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
            postRepository.softDelete(
                externalId = externalId,
                sourceUpdatedAt = sourceUpdatedAt,
                eventId = eventId,
            )
        return if (affected == 0) {
            log.info { "Delete ignored (stale): externalId=$externalId" }
            SyncResult.STALE_IGNORED
        } else {
            postRepository.findByExternalId(externalId)?.id?.let(ragWriteService::deleteAuthorPost)
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
