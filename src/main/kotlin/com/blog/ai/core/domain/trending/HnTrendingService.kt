package com.blog.ai.core.domain.trending

import com.blog.ai.storage.trending.HnTrendingEntity
import com.blog.ai.storage.trending.HnTrendingRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient

@Service
@Transactional(readOnly = true)
class HnTrendingService(
    private val hnTrendingRepository: HnTrendingRepository,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val SINGLETON_ENTITY_ID = 1
        private const val TOP_STORIES_LIMIT = 10
    }

    fun getItems(): List<HnItem> {
        val entity = hnTrendingRepository.findById(SINGLETON_ENTITY_ID).orElse(null) ?: return emptyList()
        val json = entity.items ?: return emptyList()

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

    @Transactional
    fun fetchAndSave() {
        val items = fetchTopStories()
        if (items.isEmpty()) return

        val entity = hnTrendingRepository.findById(SINGLETON_ENTITY_ID)
            .orElseGet { HnTrendingEntity.create() }
        entity.updateItems(objectMapper.writeValueAsString(items))
        hnTrendingRepository.save(entity)

        log.info { "HN trending updated: ${items.size} items" }
    }

    private fun fetchTopStories(): List<HnItem> {
        val restClient = RestClient.create()
        val topIds = restClient.get()
            .uri("https://hacker-news.firebaseio.com/v0/topstories.json")
            .retrieve()
            .body(List::class.java)
            ?.take(TOP_STORIES_LIMIT) ?: return emptyList()

        return topIds.filterNotNull().mapNotNull { id -> fetchItem(restClient, id) }
    }

    private fun fetchItem(restClient: RestClient, id: Any): HnItem? {
        return try {
            val item = restClient.get()
                .uri("https://hacker-news.firebaseio.com/v0/item/$id.json")
                .retrieve()
                .body(Map::class.java) ?: return null

            HnItem(
                title = item["title"] as? String ?: return null,
                url = item["url"] as? String,
                score = (item["score"] as? Number)?.toInt() ?: 0,
            )
        } catch (e: Exception) {
            log.debug(e) { "HN item fetch failed: id=$id" }
            null
        }
    }
}
