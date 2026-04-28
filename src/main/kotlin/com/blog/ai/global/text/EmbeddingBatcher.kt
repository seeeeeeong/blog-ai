package com.blog.ai.global.text

import org.springframework.ai.embedding.EmbeddingModel

object EmbeddingBatcher {
    private const val DEFAULT_BATCH_SIZE = 64
    private const val BATCH_TOKEN_BUDGET = 280_000

    fun embed(
        embeddingModel: EmbeddingModel,
        texts: List<String>,
        batchSize: Int = DEFAULT_BATCH_SIZE,
    ): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val out = ArrayList<FloatArray>(texts.size)
        var buffer = ArrayList<String>(batchSize)
        var bufferTokens = 0

        for (text in texts) {
            val tokens = TokenTruncator.countTokens(text)
            val wouldOverflow = buffer.size >= batchSize || bufferTokens + tokens > BATCH_TOKEN_BUDGET
            if (wouldOverflow && buffer.isNotEmpty()) {
                flush(embeddingModel, buffer, out)
                buffer = ArrayList(batchSize)
                bufferTokens = 0
            }
            buffer.add(text)
            bufferTokens += tokens
        }
        if (buffer.isNotEmpty()) flush(embeddingModel, buffer, out)
        check(out.size == texts.size) {
            "EmbeddingBatcher total output mismatch: expected=${texts.size}, got=${out.size}"
        }
        return out
    }

    private fun flush(
        embeddingModel: EmbeddingModel,
        buffer: List<String>,
        out: MutableList<FloatArray>,
    ) {
        val vectors = embeddingModel.embed(buffer)
        check(vectors.size == buffer.size) {
            "Embedding API returned ${vectors.size} vectors for ${buffer.size} inputs"
        }
        out.addAll(vectors)
    }

    fun toVectorLiteral(vector: FloatArray): String = vector.joinToString(",", "[", "]")
}
