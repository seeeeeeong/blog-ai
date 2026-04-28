package com.blog.ai.post.api.sync

import com.blog.ai.post.domain.SyncResult

data class SyncPostResponse(
    val status: SyncResult,
) {
    companion object {
        fun of(result: SyncResult) = SyncPostResponse(result)
    }
}
