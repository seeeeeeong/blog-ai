package com.blog.ai.chat.ratelimit

enum class RateLimitOutcome {
    OK,
    SESSION_LIMITED,
    IP_LIMITED,
}
