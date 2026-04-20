package com.blog.ai.core.support.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jina")
data class JinaProperties(
    val apiKey: String = "",
    val rerankUrl: String = "https://api.jina.ai/v1/rerank",
    val rerankModel: String = "jina-reranker-v2-base-multilingual",
)
