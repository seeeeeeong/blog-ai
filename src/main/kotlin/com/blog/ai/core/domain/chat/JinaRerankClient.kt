package com.blog.ai.core.domain.chat

import com.blog.ai.core.support.properties.JinaProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.document.Document
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class JinaRerankClient(
    private val jinaProperties: JinaProperties,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DOC_SNIPPET_LIMIT = 2000
        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 5_000
    }

    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(jinaProperties.rerankUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                },
            ).build()

    fun rerank(
        query: String,
        documents: List<Document>,
        topN: Int,
    ): List<Document> {
        if (documents.isEmpty()) return emptyList()
        if (jinaProperties.apiKey.isBlank()) {
            log.warn { "Jina rerank skipped: api-key not configured" }
            return documents.take(topN)
        }

        return try {
            val texts = documents.map { it.text.orEmpty().take(DOC_SNIPPET_LIMIT) }
            val body =
                mapOf(
                    "model" to jinaProperties.rerankModel,
                    "query" to query,
                    "documents" to texts,
                    "top_n" to topN,
                )

            val response =
                restClient
                    .post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${jinaProperties.apiKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JinaRerankResponse::class.java)

            val results = response?.results ?: return documents.take(topN)
            results.mapNotNull { r ->
                documents.getOrNull(r.index)?.let { doc ->
                    Document(doc.id, doc.text.orEmpty(), doc.metadata + ("rerankScore" to r.relevanceScore))
                }
            }
        } catch (e: Exception) {
            log.warn(e) { "Jina rerank failed, falling back to original order" }
            documents.take(topN)
        }
    }
}
