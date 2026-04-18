package com.blog.ai.core.domain.post

enum class SyncResult {
    APPLIED,
    STALE_IGNORED,
    TOMBSTONED,
}
