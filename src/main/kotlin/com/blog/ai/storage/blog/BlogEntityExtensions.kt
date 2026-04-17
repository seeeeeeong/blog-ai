package com.blog.ai.storage.blog

import com.blog.ai.core.domain.blog.Blog

fun BlogEntity.toBlog() = Blog(
    id = requireNotNull(id) { "BlogEntity.id must not be null after persistence" },
    name = name,
    company = company,
    rssUrl = rssUrl,
    homeUrl = homeUrl,
    active = active,
)
