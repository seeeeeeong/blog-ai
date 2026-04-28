package com.blog.ai.post.sync

import com.blog.ai.post.sync.EventType
import com.blog.ai.post.sync.SyncPost
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

data class SyncPostRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val externalId: String,
    @field:NotNull
    val eventType: EventType,
    @field:NotNull
    val sourceUpdatedAt: OffsetDateTime,
    @field:Size(max = 64)
    val eventId: String? = null,
    val title: String? = null,
    val content: String? = null,
    val url: String? = null,
    @field:Size(max = 100)
    val author: String? = null,
    val publishedAt: OffsetDateTime? = null,
) {
    fun toCommand(title: String): SyncPost =
        SyncPost(
            externalId = externalId,
            title = title,
            content = content,
            url = url,
            author = author,
            publishedAt = publishedAt,
            sourceUpdatedAt = sourceUpdatedAt,
            eventId = eventId,
        )
}
