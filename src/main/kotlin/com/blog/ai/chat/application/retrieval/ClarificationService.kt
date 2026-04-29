package com.blog.ai.chat.application.retrieval

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

@Service
class ClarificationService(
    private val chatClientBuilder: ChatClient.Builder,
    private val chatMemory: ChatMemory,
    @Value("classpath:prompts/retrieval/clarification.st")
    systemPromptResource: Resource,
) {
    private val systemPrompt: String = systemPromptResource.getContentAsString(StandardCharsets.UTF_8)

    companion object {
        private val log = KotlinLogging.logger {}
        private const val HISTORY_LIMIT = 6
        private const val HISTORY_SNIPPET = 200
        private const val MAX_CLARIFY_LENGTH = 200
        private const val EMERGENCY_FALLBACK = "어떤 부분을 더 자세히 알고 싶으신가요?"
    }

    fun clarify(
        sessionKey: String,
        question: String,
        plannerHint: String?,
    ): String {
        val response = generate(sessionKey, question, plannerHint)
        chatMemory.add(
            sessionKey,
            listOf(UserMessage(question), AssistantMessage(response)),
        )
        return response
    }

    private fun generate(
        sessionKey: String,
        question: String,
        plannerHint: String?,
    ): String {
        val historyText = formatHistory(chatMemory.get(sessionKey))
        return try {
            val raw =
                chatClientBuilder
                    .build()
                    .prompt()
                    .system(systemPrompt)
                    .user(buildUserPrompt(historyText, question, plannerHint))
                    .call()
                    .content()
            sanitize(raw) ?: EMERGENCY_FALLBACK
        } catch (e: Exception) {
            log.warn(e) { "Clarify generation failed, using emergency fallback" }
            EMERGENCY_FALLBACK
        }
    }

    private fun sanitize(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val firstLine =
            trimmed
                .lineSequence()
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return null
        return firstLine.take(MAX_CLARIFY_LENGTH)
    }

    private fun formatHistory(history: List<Message>): String =
        if (history.isEmpty()) {
            ""
        } else {
            history
                .takeLast(HISTORY_LIMIT)
                .filter { it.messageType == MessageType.USER || it.messageType == MessageType.ASSISTANT }
                .joinToString("\n") { "${it.messageType.value}: ${it.text?.take(HISTORY_SNIPPET)}" }
        }

    private fun buildUserPrompt(
        historyText: String,
        question: String,
        plannerHint: String?,
    ): String {
        val hint = plannerHint?.takeIf { it.isNotBlank() }
        val hintLine = if (hint == null) "" else "\nHint (do not echo verbatim): $hint"
        val historyBlock = if (historyText.isBlank()) "" else "Conversation history:\n$historyText\n\n"
        return "${historyBlock}Latest user message: $question$hintLine"
    }
}
