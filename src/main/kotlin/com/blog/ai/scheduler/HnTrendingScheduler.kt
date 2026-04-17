package com.blog.ai.scheduler

import com.blog.ai.core.domain.trending.HnTrendingService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HnTrendingScheduler(
    private val hnTrendingService: HnTrendingService,
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(cron = "0 0 */6 * * *")
    fun fetch() {
        try {
            hnTrendingService.fetchAndSave()
        } catch (e: Exception) {
            log.error(e) { "HN trending update failed" }
        }
    }
}
