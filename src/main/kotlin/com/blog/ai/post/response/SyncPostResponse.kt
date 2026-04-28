package com.blog.ai.post.response

import com.blog.ai.post.model.SyncResult

data class SyncPostResponse(
    val status: SyncResult,
) {
    companion object {
        fun of(result: SyncResult) = SyncPostResponse(result)
    }
}
