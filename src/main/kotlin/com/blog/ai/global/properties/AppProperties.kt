package com.blog.ai.global.properties

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

@ConfigurationProperties(prefix = "rag.contextual")
data class RagContextualProperties(
    val enabled: Boolean = true,
    val minDocumentLength: Int = 1000,
    val maxContextTokens: Int = 120,
)
