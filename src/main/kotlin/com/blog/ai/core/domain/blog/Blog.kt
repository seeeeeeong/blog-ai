package com.blog.ai.core.domain.blog

data class Blog(
    val id: Long,
    val name: String,
    val company: String,
    val rssUrl: String,
    val homeUrl: String,
    val active: Boolean,
)
