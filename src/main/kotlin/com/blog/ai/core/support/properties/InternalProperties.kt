package com.blog.ai.core.support.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "internal")
data class InternalProperties(
    val apiKey: String,
)
