package com.blog.ai.storage.blog

import com.blog.ai.core.domain.blog.Blog

fun BlogEntity.toBlog() = Blog(
    id = id,
    name = name,
    company = company,
    rssUrl = rssUrl,
    homeUrl = homeUrl,
    active = active,
)
