package com.blog.ai.post.sync

import com.blog.ai.post.sync.SyncResult

data class SyncPostResponse(
    val status: SyncResult,
) {
    companion object {
        fun of(result: SyncResult) = SyncPostResponse(result)
    }
}
