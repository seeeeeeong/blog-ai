package com.blog.ai.chat.domain

enum class RateLimitOutcome {
    OK,
    SESSION_LIMITED,
    IP_LIMITED,
}
