package com.blog.ai.rag

import com.blog.ai.global.properties.RagContextualProperties
import com.blog.ai.global.text.TokenTruncator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class ChunkEnricher(
    private val chatClientBuilder: ChatClient.Builder,
    private val ragContextualProperties: RagContextualProperties,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DOCUMENT_TOKEN_BUDGET = 6000

        private val SYSTEM_PROMPT =
            """
            You write a single short context paragraph that situates a chunk
            of text inside its source document, to improve retrieval against
            the chunk later.

            Rules:
            1. Output the context only — no preamble, no quotes, no labels.
            2. Up to 2 sentences, around 50-80 words.
            3. Mention the document's topic and what aspect this chunk covers.
            4. Use the same language as the chunk (Korean stays Korean).
            5. Do not invent facts not present in the document.
            """.trimIndent()
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
                    .system(SYSTEM_PROMPT)
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
