package com.blog.ai.global.text

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import java.nio.charset.StandardCharsets

object PromptLoader {
    private val PARTIAL_REGEX = Regex("""\{\{>\s*([\w/\-]+)\s*\}\}""")
    private const val PARTIALS_PREFIX = "prompts/_partials/"
    private const val PARTIAL_EXTENSION = ".st"

    fun load(resource: Resource): String {
        val raw = resource.getContentAsString(StandardCharsets.UTF_8)
        return resolvePartials(raw)
    }

    internal fun resolvePartials(content: String): String =
        PARTIAL_REGEX.replace(content) { match ->
            val partialName = match.groupValues[1].trim()
            val partialPath = "$PARTIALS_PREFIX$partialName$PARTIAL_EXTENSION"
            ClassPathResource(partialPath)
                .getContentAsString(StandardCharsets.UTF_8)
                .trimEnd()
        }
}
