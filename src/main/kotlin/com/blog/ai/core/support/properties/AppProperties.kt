package com.blog.ai.core.support.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    val apiKey: String,
)

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOrigins: String = "",
)

@ConfigurationProperties(prefix = "internal")
data class InternalProperties(
    val apiKey: String,
)

@ConfigurationProperties(prefix = "jina")
data class JinaProperties(
    val apiKey: String = "",
    val rerankUrl: String = "https://api.jina.ai/v1/rerank",
    val rerankModel: String = "jina-reranker-v2-base-multilingual",
)
