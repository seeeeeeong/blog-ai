package com.blog.ai.core.domain.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class ChatQueryExpander(
    private val chatClientBuilder: ChatClient.Builder,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_VARIANTS = 2
        private const val NONE_SIGNAL = "NONE"
        private const val MAX_VARIANT_LENGTH = 100

        private val EXPAND_PROMPT =
            """
            You generate alternative phrasings of a search query to broaden recall
            against a Korean/English tech blog corpus.

            Rules:
            1. Output up to 2 alternative phrasings, one per line.
            2. Each variant must cover different word choices: swap Korean
               domain terms with their English equivalents (or vice versa),
               or use a closely related synonym commonly used in tech blogs.
            3. No numbering, no quotes, no commentary. Just one phrasing per line.
            4. Each variant must stay under 100 characters and remain a search
               query (not a sentence answer).
            5. Do not repeat the original query.
            6. If the query is already specific enough that variants would only
               add noise, output the single line: NONE
            """.trimIndent()
    }

    fun expand(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return try {
            val response =
                chatClientBuilder
                    .build()
                    .prompt()
                    .system(EXPAND_PROMPT)
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
