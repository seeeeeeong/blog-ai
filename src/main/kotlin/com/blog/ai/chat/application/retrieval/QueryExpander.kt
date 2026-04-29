package com.blog.ai.chat.application.retrieval

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component("chatQueryExpander")
class QueryExpander(
    private val chatClientBuilder: ChatClient.Builder,
    @Value("classpath:prompts/retrieval/query-expander.st")
    expandPromptResource: Resource,
) {
    private val expandPrompt: String = expandPromptResource.getContentAsString(StandardCharsets.UTF_8)

    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_VARIANTS = 2
        private const val NONE_SIGNAL = "NONE"
        private const val MAX_VARIANT_LENGTH = 100
    }

    fun expand(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return try {
            val response =
                chatClientBuilder
                    .build()
                    .prompt()
                    .system(expandPrompt)
                    .user(query)
                    .call()
                    .content()
                    ?.trim()
                    ?: return listOf(query)

            if (response == NONE_SIGNAL) return listOf(query)

            val variants =
                response
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != NONE_SIGNAL && it != query }
                    .map { it.take(MAX_VARIANT_LENGTH) }
                    .distinct()
                    .take(MAX_VARIANTS)

            log.debug { "Query expansion: '$query' -> $variants" }
            (listOf(query) + variants).distinct()
        } catch (e: Exception) {
            log.warn(e) { "Query expansion failed, using original" }
            listOf(query)
        }
    }
}
