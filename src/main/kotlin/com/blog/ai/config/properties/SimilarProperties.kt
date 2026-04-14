package com.blog.ai.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "similar")
data class SimilarProperties(
    val topK: Int = 5,
)
