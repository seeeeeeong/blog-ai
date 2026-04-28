package com.blog.ai.chat.model

import java.time.Duration

data class RateLimitRequest(
    val sessionKey: String,
    val ipKey: String,
    val sessionMax: Int,
    val ipMax: Int,
    val sessionTtl: Duration,
    val ipTtl: Duration,
)
