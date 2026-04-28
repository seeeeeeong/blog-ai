package com.blog.ai.blog.model

data class Blog(
    val id: Long,
    val name: String,
    val company: String,
    val rssUrl: String,
    val homeUrl: String,
    val active: Boolean,
)
