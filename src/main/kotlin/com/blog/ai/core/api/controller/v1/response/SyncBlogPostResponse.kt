package com.blog.ai.core.api.controller.v1.response

import com.blog.ai.core.domain.post.SyncResult

data class SyncBlogPostResponse(
    val status: SyncResult,
) {
    companion object {
        fun of(result: SyncResult) = SyncBlogPostResponse(result)
    }
}
