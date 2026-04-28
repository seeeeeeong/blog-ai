package com.blog.ai.blog

import com.blog.ai.blog.Blog
import com.blog.ai.blog.BlogEntity

fun BlogEntity.toBlog() =
    Blog(
        id = requireNotNull(id) { "BlogEntity.id must not be null after persistence" },
        name = name,
        company = company,
        rssUrl = rssUrl,
        homeUrl = homeUrl,
        active = active,
    )
