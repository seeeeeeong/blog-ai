package com.blog.ai.scheduler

import com.blog.ai.core.domain.trending.HnItem
import com.blog.ai.storage.trending.HnTrendingEntity
import com.blog.ai.storage.trending.HnTrendingRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class HnTrendingScheduler(
    private val hnTrendingRepository: HnTrendingRepository,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val TOP_STORIES_LIMIT = 10
        private const val SINGLETON_ENTITY_ID = 1
    }

    @Scheduled(cron = "0 0 */6 * * *")
    fun fetch() {
        try {
            val restClient = RestClient.create()
            val topIds = restClient.get()
                .uri("https://hacker-news.firebaseio.com/v0/topstories.json")
                .retrieve()
                .body(List::class.java)
                ?.take(TOP_STORIES_LIMIT) ?: return

            val items = topIds.mapNotNull { id ->
                try {
                    val item = restClient.get()
                        .uri("https://hacker-news.firebaseio.com/v0/item/$id.json")
                        .retrieve()
                        .body(Map::class.java) ?: return@mapNotNull null

                    HnItem(
                        title = item["title"] as? String ?: return@mapNotNull null,
                        url = item["url"] as? String,
                        score = (item["score"] as? Number)?.toInt() ?: 0,
                    )
                } catch (e: Exception) {
                    log.debug(e) { "HN item fetch failed: id=$id" }
                    null
                }
            }

            val entity = hnTrendingRepository.findById(SINGLETON_ENTITY_ID)
                .orElseGet { HnTrendingEntity.create() }
            entity.updateItems(objectMapper.writeValueAsString(items))
            hnTrendingRepository.save(entity)

            log.info { "HN trending updated: ${items.size} items" }
        } catch (e: Exception) {
            log.error(e) { "HN trending update failed" }
        }
    }
}
