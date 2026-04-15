package com.blog.ai.core.domain.trending

import com.blog.ai.storage.trending.HnTrendingRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class HnItem(
    val title: String,
    val url: String?,
    val score: Int,
)

@Service
@Transactional(readOnly = true)
class HnTrendingService(
    private val hnTrendingRepository: HnTrendingRepository,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private const val SINGLETON_ENTITY_ID = 1
    }

    private val log = LoggerFactory.getLogger(javaClass)

    fun getItems(): List<HnItem> {
        val entity = hnTrendingRepository.findById(SINGLETON_ENTITY_ID).orElse(null) ?: return emptyList()
        val json = entity.items ?: return emptyList()

        return try {
            objectMapper.readValue(
                json,
                objectMapper.typeFactory.constructCollectionType(List::class.java, HnItem::class.java),
            )
        } catch (e: Exception) {
            log.warn("HN trending parse failed: {}", e.message)
            emptyList()
        }
    }
}
