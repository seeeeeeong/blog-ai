package com.blog.ai.core.api.controller.v1

import com.blog.ai.core.api.controller.v1.request.SyncBlogPostRequest
import com.blog.ai.core.api.controller.v1.response.SyncBlogPostResponse
import com.blog.ai.core.domain.post.BlogPostSyncService
import com.blog.ai.core.domain.post.EventType
import com.blog.ai.core.domain.post.SyncResult
import com.blog.ai.core.support.error.CoreException
import com.blog.ai.core.support.error.ErrorType
import com.blog.ai.core.support.properties.InternalProperties
import com.blog.ai.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/blog-posts")
class InternalBlogPostController(
    private val internalProperties: InternalProperties,
    private val blogPostSyncService: BlogPostSyncService,
) {
    @PostMapping("/sync")
    fun sync(
        @RequestHeader("X-Internal-Key") internalKey: String,
        @Valid @RequestBody request: SyncBlogPostRequest,
    ): ApiResponse<SyncBlogPostResponse> {
        requireInternalKey(internalKey)
        val result = dispatch(request)
        return ApiResponse.success(SyncBlogPostResponse.of(result))
    }

    private fun dispatch(request: SyncBlogPostRequest): SyncResult =
        when (request.eventType) {
            EventType.UPSERT -> {
                val title = requireUpsertTitle(request)
                blogPostSyncService.upsert(request.toCommand(title))
            }

            EventType.DELETE -> {
                blogPostSyncService.softDelete(
                    externalId = request.externalId,
                    sourceUpdatedAt = request.sourceUpdatedAt,
                    eventId = request.eventId,
                )
            }
        }

    private fun requireUpsertTitle(request: SyncBlogPostRequest): String {
        val title = request.title
        if (title.isNullOrBlank()) {
            throw CoreException(ErrorType.BLOG_POST_INVALID_PAYLOAD)
        }
        return title
    }

    private fun requireInternalKey(key: String) {
        if (key == internalProperties.apiKey) return
        throw CoreException(ErrorType.UNAUTHORIZED)
    }
}
