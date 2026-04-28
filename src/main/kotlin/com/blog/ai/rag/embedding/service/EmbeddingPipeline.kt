package com.blog.ai.rag.embedding.service

import com.blog.ai.global.text.EmbeddingBatcher
import com.blog.ai.global.text.TextSplitter
import com.blog.ai.global.text.TokenTruncator
import com.blog.ai.rag.embedding.model.ChunkEmbedding
import com.blog.ai.rag.embedding.model.ChunkEmbeddingJob
import com.blog.ai.rag.embedding.model.DocumentEmbedding
import com.blog.ai.rag.embedding.model.EmbeddingDocument
import com.blog.ai.rag.service.ChunkEnricher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Service

@Service
class EmbeddingPipeline(
    private val embeddingModel: EmbeddingModel,
    private val chunkEnricher: ChunkEnricher,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MAX_EMBED_TOKENS = 7500
    }

    fun embedBatch(
        documents: List<EmbeddingDocument>,
        enrichChunks: Boolean,
        onError: (EmbeddingDocument, String) -> Unit,
    ): List<DocumentEmbedding>? {
        if (documents.isEmpty()) return emptyList()

        val docTexts = documents.map(::documentText)
        val docVectors = runBatch("doc", docTexts, documents, onError) ?: return null

        val chunkJobs = documents.map { buildChunkJobs(it, enrichChunks) }
        val chunkTexts =
            documents.zip(chunkJobs).flatMap { (document, jobs) ->
                jobs.map { it.embedText(document.title) }
            }
        val chunkVectors =
            if (chunkTexts.isEmpty()) {
                emptyList()
            } else {
                runBatch("chunk", chunkTexts, documents, onError) ?: return null
            }

        return buildEmbeddings(documents, docVectors, chunkJobs, chunkVectors)
    }

    fun embedOne(
        document: EmbeddingDocument,
        enrichChunks: Boolean,
    ): DocumentEmbedding {
        val jobs = buildChunkJobs(document, enrichChunks)
        val vectors =
            EmbeddingBatcher.embed(
                embeddingModel,
                listOf(documentText(document)) + jobs.map { it.embedText(document.title) },
            )
        return DocumentEmbedding(
            docVector = EmbeddingBatcher.toVectorLiteral(vectors[0]),
            chunks = buildChunkEmbeddings(jobs, vectors.drop(1), 0),
        )
    }

    private fun documentText(document: EmbeddingDocument): String =
        TokenTruncator.truncate("${document.title} ${document.content}", MAX_EMBED_TOKENS)

    private fun buildChunkJobs(
        document: EmbeddingDocument,
        enrichChunks: Boolean,
    ): List<ChunkEmbeddingJob> {
        if (document.content.isBlank()) return emptyList()
        return TextSplitter.split(document.content).map { rawChunk ->
            val context =
                if (enrichChunks) {
                    chunkEnricher.enrich(document.title, document.content, rawChunk)
                } else {
                    null
                }
            ChunkEmbeddingJob(rawChunk = rawChunk, context = context)
        }
    }

    private fun buildEmbeddings(
        documents: List<EmbeddingDocument>,
        docVectors: List<FloatArray>,
        chunkJobs: List<List<ChunkEmbeddingJob>>,
        chunkVectors: List<FloatArray>,
    ): List<DocumentEmbedding> {
        var cursor = 0
        return documents.mapIndexed { index, document ->
            val jobs = chunkJobs[index]
            val chunks = buildChunkEmbeddings(jobs, chunkVectors, cursor)
            cursor += jobs.size
            DocumentEmbedding(
                docVector = EmbeddingBatcher.toVectorLiteral(docVectors[index]),
                chunks = chunks,
            )
        }
    }

    private fun buildChunkEmbeddings(
        jobs: List<ChunkEmbeddingJob>,
        chunkVectors: List<FloatArray>,
        cursor: Int,
    ): List<ChunkEmbedding> =
        jobs.mapIndexed { index, job ->
            ChunkEmbedding(
                chunkIndex = index,
                content = job.storedContent(),
                embedding = EmbeddingBatcher.toVectorLiteral(chunkVectors[cursor + index]),
            )
        }

    private fun runBatch(
        kind: String,
        texts: List<String>,
        documents: List<EmbeddingDocument>,
        onError: (EmbeddingDocument, String) -> Unit,
    ): List<FloatArray>? =
        try {
            EmbeddingBatcher.embed(embeddingModel, texts)
        } catch (e: Exception) {
            log.error(e) { "Batch $kind embedding failed: size=${texts.size}" }
            documents.forEach { onError(it, e.message ?: "unknown") }
            null
        }
}
