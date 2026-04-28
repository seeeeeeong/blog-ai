package com.blog.ai.blog.infrastructure

import com.blog.ai.blog.domain.Blog
import com.blog.ai.blog.infrastructure.BlogEntity

fun BlogEntity.toBlog() =
    Blog(
        id = requireNotNull(id) { "BlogEntity.id must not be null after persistence" },
        name = name,
        company = company,
        rssUrl = rssUrl,
        homeUrl = homeUrl,
        active = active,
    )
