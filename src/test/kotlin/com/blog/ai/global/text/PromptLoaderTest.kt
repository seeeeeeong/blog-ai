package com.blog.ai.global.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class PromptLoaderTest {
    @Test
    fun `chat system prompt resolves author-identity partial inline`() {
        val rendered = PromptLoader.load(ClassPathResource("prompts/chat/system.st"))

        assertFalse(rendered.contains("{{>"), "all partial markers must be resolved")
        assertTrue(
            rendered.contains("\"작성자님\" refers to EXACTLY ONE person"),
            "canonical author-identity definition must appear after substitution",
        )
    }

    @Test
    fun `chat rag-context prompt keeps Spring AI placeholders intact while resolving partials`() {
        val rendered = PromptLoader.load(ClassPathResource("prompts/chat/rag-context.st"))

        assertFalse(rendered.contains("{{>"), "partial markers must be resolved")
        assertTrue(rendered.contains("{context}"), "Spring AI {context} placeholder must remain")
        assertTrue(rendered.contains("{query}"), "Spring AI {query} placeholder must remain")
        assertTrue(rendered.contains("CORRECT: \"작성자님은 pgvector를 선택했습니다\""))
    }

    @Test
    fun `resolvePartials replaces marker with partial body and leaves other text untouched`() {
        val input = "PRE\n{{> author-identity}}\nPOST"

        val result = PromptLoader.resolvePartials(input)

        assertTrue(result.startsWith("PRE\n"))
        assertTrue(result.endsWith("\nPOST"))
        assertFalse(result.contains("{{>"))
    }

    @Test
    fun `content with no partial markers passes through unchanged`() {
        val input = "no markers here. {context} stays. {query} stays."

        val result = PromptLoader.resolvePartials(input)

        assertEquals(input, result)
    }
}
