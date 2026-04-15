package com.blog.ai.core.support.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "similar")
data class SimilarProperties(
    val topK: Int = 5,
)
