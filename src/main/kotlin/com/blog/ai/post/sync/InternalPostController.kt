package com.blog.ai.post.sync

import com.blog.ai.global.error.AppException
import com.blog.ai.global.error.ErrorCode
import com.blog.ai.global.properties.InternalProperties
import com.blog.ai.global.response.ApiResponse
import com.blog.ai.post.sync.EventType
import com.blog.ai.post.sync.PostSyncService
import com.blog.ai.post.sync.SyncPostRequest
import com.blog.ai.post.sync.SyncPostResponse
import com.blog.ai.post.sync.SyncResult
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
