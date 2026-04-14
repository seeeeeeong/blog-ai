package com.blog.ai.storage.blog

data class Blog(
    val id: Long,
    val name: String,
    val company: String,
    val rssUrl: String,
    val homeUrl: String,
    val active: Boolean,
)

fun BlogEntity.toBlog() = Blog(
    id = id,
    name = name,
    company = company,
    rssUrl = rssUrl,
    homeUrl = homeUrl,
    active = active,
)
