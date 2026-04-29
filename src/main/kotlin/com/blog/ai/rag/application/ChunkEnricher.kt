package com.blog.ai.rag.application

import com.blog.ai.global.properties.RagContextualProperties
import com.blog.ai.global.text.TokenTruncator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class ChunkEnricher(
    private val chatClientBuilder: ChatClient.Builder,
    private val ragContextualProperties: RagContextualProperties,
    @Value("classpath:prompts/rag/chunk-enrich.st")
    systemPromptResource: Resource,
) {
    private val systemPrompt: String = systemPromptResource.getContentAsString(StandardCharsets.UTF_8)

    companion object {
        private val log = KotlinLogging.logger {}
        private const val DOCUMENT_TOKEN_BUDGET = 6000
    }

    fun enrich(
        title: String,
        document: String,
        chunk: String,
    ): String? {
        if (!ragContextualProperties.enabled) return null
        if (document.length < ragContextualProperties.minDocumentLength) return null
        if (chunk.isBlank()) return null

        val truncatedDoc = TokenTruncator.truncate(document, DOCUMENT_TOKEN_BUDGET)
        return try {
            val raw =
                chatClientBuilder
                    .build()
                    .prompt()
                    .system(systemPrompt)
                    .user(buildUserPrompt(title, truncatedDoc, chunk))
                    .call()
                    .content()
                    ?.trim()
                    ?: return null
            TokenTruncator.truncate(raw, ragContextualProperties.maxContextTokens).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            log.warn(e) { "Chunk context enrichment failed: title='${title.take(60)}'" }
            null
        }
    }

    private fun buildUserPrompt(
        title: String,
        document: String,
        chunk: String,
    ): String =
        """
        <document title="${title.take(200)}">
        $document
        </document>

        <chunk>
        $chunk
        </chunk>

        Write the situating context now.
        """.trimIndent()
}
