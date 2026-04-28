package com.blog.ai.rag.service

import com.blog.ai.rag.model.RagChunkHit
import com.blog.ai.rag.model.RagSearchQuery
import com.blog.ai.rag.model.RagSourceType
import com.blog.ai.rag.repository.RagChunkRepository
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
