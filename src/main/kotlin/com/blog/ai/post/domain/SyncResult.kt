package com.blog.ai.post.domain

enum class SyncResult {
    APPLIED,
    STALE_IGNORED,
    TOMBSTONED,
}
