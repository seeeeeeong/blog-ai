package com.blog.ai.blog.mapper

import com.blog.ai.blog.entity.BlogEntity
import com.blog.ai.blog.model.Blog

fun BlogEntity.toBlog() =
    Blog(
        id = requireNotNull(id) { "BlogEntity.id must not be null after persistence" },
        name = name,
        company = company,
        rssUrl = rssUrl,
        homeUrl = homeUrl,
        active = active,
    )
