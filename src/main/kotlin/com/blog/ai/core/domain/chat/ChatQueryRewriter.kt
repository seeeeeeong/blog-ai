package com.blog.ai.core.domain.chat

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.stereotype.Component

@Component
class ChatQueryRewriter(
    private val chatClientBuilder: ChatClient.Builder,
    private val chatMemory: ChatMemory,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_HISTORY_FOR_REWRITE = 6

        private val REWRITE_PROMPT =
            """
            You are a query rewriter. Given the conversation history and the latest
            user question, rewrite the question into a standalone search query that
            can be used to search a tech blog database.

            Rules:
            1. If the question is already self-contained, return it as-is.
            2. Resolve pronouns and references (e.g. "그거", "아까 말한 것") using context.
            3. Keep the rewritten query concise (under 100 characters).
            4. Output ONLY the rewritten query, nothing else.
            5. Preserve the original language (Korean/English).
            """.trimIndent()
    }

    fun rewrite(
        sessionId: String,
        question: String,
    ): String {
        val history = chatMemory.get(sessionId)
        if (history.isEmpty()) return question

        val recentHistory =
            history
                .takeLast(MAX_HISTORY_FOR_REWRITE)
                .joinToString("\n") { "${it.messageType.value}: ${it.text?.take(200)}" }

        return try {
            val rewrittenQuery =
                chatClientBuilder
                    .build()
                    .prompt()
                    .system(REWRITE_PROMPT)
                    .user("Conversation history:\n$recentHistory\n\nLatest question: $question")
                    .call()
                    .content()
                    ?.trim()
                    ?: question

            log.debug { "Query rewrite: '$question' -> '$rewrittenQuery'" }
            rewrittenQuery
        } catch (e: Exception) {
            log.warn(e) { "Query rewrite failed, using original question" }
            question
        }
    }
}
