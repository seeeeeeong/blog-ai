package com.blog.ai.rag

import com.blog.ai.rag.RagChunkHit
import com.blog.ai.rag.RagChunkRepository
import com.blog.ai.rag.RagSearchQuery
import com.blog.ai.rag.RagSourceType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RagSearchService(
    private val ragChunkRepository: RagChunkRepository,
) {
    fun search(query: RagSearchQuery): List<RagChunkHit> = ragChunkRepository.searchHybrid(query)

    fun findDocumentVector(
        sourceType: RagSourceType,
        sourceId: Long,
    ): String? = ragChunkRepository.findDocumentVector(sourceType, sourceId)
}
