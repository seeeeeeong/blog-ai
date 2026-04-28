package com.blog.ai

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {
    private val sourceRoot = Path("src/main/kotlin")

    @Test
    fun `main sources do not reintroduce legacy packages or committers`() {
        val violations =
            kotlinFiles()
                .filter { file ->
                    val text = file.readText()
                    text.contains("com.blog.ai.core") ||
                        text.contains("com.blog.ai.storage") ||
                        text.contains("Committer") ||
                        text.contains("CommitCommand")
                }.map { it.relativeTo(sourceRoot).toString() }
                .toList()

        assertTrue(violations.isEmpty(), "Forbidden legacy patterns found: $violations")
    }

    @Test
    fun `RagChunkRepository is only accessed inside rag module`() {
        val violations =
            kotlinFiles()
                .filterNot { it.relativeTo(sourceRoot).toString().startsWith("com/blog/ai/rag/") }
                .filter { file ->
                    file.readText().contains("RagChunkRepository")
                }.map { it.relativeTo(sourceRoot).toString() }
                .toList()

        assertTrue(
            violations.isEmpty(),
            "Use RagSearchService / RagWriteService instead of RagChunkRepository: $violations",
        )
    }

    private fun kotlinFiles() =
        Files
            .walk(sourceRoot)
            .asSequence()
            .filter { it.isRegularFile() && it.extension == "kt" }
}
