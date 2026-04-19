package com.blog.ai.core.support.text

object TextSplitter {
    private const val CHUNK_SIZE = 1200
    private const val CHUNK_OVERLAP = 150
    private val SEPARATORS = listOf("\n\n", "\n", ". ", " ")

    fun split(text: String): List<String> {
        if (text.length <= CHUNK_SIZE) return listOf(text)
        return doSplit(text, SEPARATORS)
    }

    private fun doSplit(
        text: String,
        separators: List<String>,
    ): List<String> {
        if (text.length <= CHUNK_SIZE) return listOf(text)

        val separator = separators.firstOrNull { text.contains(it) } ?: return splitWithOverlap(text)
        val parts = text.split(separator)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (part in parts) {
            val candidate = if (current.isEmpty()) part else "${current}${separator}$part"

            if (candidate.length > CHUNK_SIZE && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
                current.append(part)
            } else {
                current.clear()
                current.append(candidate)
            }
        }

        if (current.isNotBlank()) {
            chunks.add(current.toString().trim())
        }

        return chunks
            .flatMap { chunk ->
                if (chunk.length > CHUNK_SIZE && separators.size > 1) {
                    doSplit(chunk, separators.drop(1))
                } else {
                    listOf(chunk)
                }
            }.let { addOverlap(it) }
    }

    private fun splitWithOverlap(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + CHUNK_SIZE).coerceAtMost(text.length)
            chunks.add(text.substring(start, end))
            start = end - CHUNK_OVERLAP
            if (start >= text.length - CHUNK_OVERLAP) break
        }
        return chunks
    }

    private fun addOverlap(chunks: List<String>): List<String> {
        if (chunks.size <= 1) return chunks

        return chunks.mapIndexed { index, chunk ->
            if (index == 0) return@mapIndexed chunk

            val prevChunk = chunks[index - 1]
            val overlapText = prevChunk.takeLast(CHUNK_OVERLAP)
            "$overlapText$chunk"
        }
    }
}
