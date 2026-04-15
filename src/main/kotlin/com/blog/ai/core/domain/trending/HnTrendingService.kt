package com.blog.ai.core.domain.trending

import com.blog.ai.storage.trending.HnTrendingRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class HnTrendingService(
    private val hnTrendingRepository: HnTrendingRepository,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val SINGLETON_ENTITY_ID = 1
    }

    fun getItems(): List<HnItem> {
        val entity = hnTrendingRepository.findById(SINGLETON_ENTITY_ID).orElse(null) ?: return emptyList()
        val json = entity.getItemsJson() ?: return emptyList()

        return try {
            objectMapper.readValue(
                json,
                objectMapper.typeFactory.constructCollectionType(List::class.java, HnItem::class.java),
            )
        } catch (e: Exception) {
            log.warn(e) { "HN trending parse failed" }
            emptyList()
        }
    }
}
