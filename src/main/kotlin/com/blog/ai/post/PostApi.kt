package com.blog.ai.post

import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import com.blog.ai.global.properties.InternalProperties
import com.blog.ai.global.response.ApiResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/v1/internal/blog-posts")
class InternalPostController(
    private val internalProperties: InternalProperties,
    private val postSyncService: PostSyncService,
) {
    @PostMapping("/sync")
    fun sync(
        @RequestHeader("X-Internal-Key") internalKey: String,
        @Valid @RequestBody request: SyncPostRequest,
    ): ApiResponse<SyncPostResponse> {
        requireInternalKey(internalKey)
        val result = dispatch(request)
        return ApiResponse.success(SyncPostResponse.of(result))
    }

    private fun dispatch(request: SyncPostRequest): SyncResult =
        when (request.eventType) {
            EventType.UPSERT -> {
                val title = requireUpsertTitle(request)
                postSyncService.upsert(request.toCommand(title))
            }

            EventType.DELETE -> {
                postSyncService.softDelete(
                    externalId = request.externalId,
                    sourceUpdatedAt = request.sourceUpdatedAt,
                    eventId = request.eventId,
                )
            }
        }

    private fun requireUpsertTitle(request: SyncPostRequest): String {
        val title = request.title
        if (title.isNullOrBlank()) {
            throw AppException(ErrorCode.BLOG_POST_INVALID_PAYLOAD)
        }
        return title
    }

    private fun requireInternalKey(key: String) {
        if (key == internalProperties.apiKey) return
        throw AppException(ErrorCode.UNAUTHORIZED)
    }
}

@RestController
@RequestMapping("/api/v1/similar")
class SimilarPostController(
    private val similarPostService: SimilarPostService,
) {
    @GetMapping
    fun findSimilar(
        @RequestParam postId: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ApiResponse<SimilarResponse> {
        val result = similarPostService.findSimilar(postId, limit)
        return ApiResponse.success(SimilarResponse.of(result))
    }
}

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

data class SyncPostResponse(
    val status: SyncResult,
) {
    companion object {
        fun of(result: SyncResult) = SyncPostResponse(result)
    }
}

data class SimilarResponse(
    val status: SimilarStatus,
    val items: List<SimilarItem>,
) {
    companion object {
        fun of(result: SimilarResult) =
            SimilarResponse(
                status = result.status,
                items = result.items.map(SimilarItem.Companion::of),
            )
    }
}

data class SimilarItem(
    val id: Long,
    val title: String,
    val url: String,
    val company: String,
    val score: Double,
) {
    companion object {
        fun of(article: SimilarArticle) =
            SimilarItem(
                id = article.id,
                title = article.title,
                url = article.url,
                company = article.company,
                score = article.score,
            )
    }
}
