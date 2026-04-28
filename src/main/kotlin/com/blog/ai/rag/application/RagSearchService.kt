package com.blog.ai.rag.application

import com.blog.ai.rag.domain.RagChunkHit
import com.blog.ai.rag.domain.RagSearchQuery
import com.blog.ai.rag.domain.RagSourceType
import com.blog.ai.rag.infrastructure.RagChunkRepository
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
