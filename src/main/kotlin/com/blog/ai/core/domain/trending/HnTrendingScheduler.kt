package com.blog.ai.core.domain.trending

import com.blog.ai.storage.trending.HnTrendingEntity
import com.blog.ai.storage.trending.HnTrendingRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.OffsetDateTime

@Component
class HnTrendingScheduler(
    private val hnTrendingRepository: HnTrendingRepository,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private const val TOP_STORIES_LIMIT = 10
        private const val SINGLETON_ENTITY_ID = 1
    }

    private val log = LoggerFactory.getLogger(javaClass)

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
                    null
                }
            }

            val entity = hnTrendingRepository.findById(SINGLETON_ENTITY_ID).orElse(HnTrendingEntity())
            entity.items = objectMapper.writeValueAsString(items)
            entity.fetchedAt = OffsetDateTime.now()
            hnTrendingRepository.save(entity)

            log.info("HN trending updated: {} items", items.size)
        } catch (e: Exception) {
            log.error("HN trending update failed: {}", e.message)
        }
    }
}
