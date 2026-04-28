package com.blog.ai.job

import com.blog.ai.chat.application.ratelimit.RateLimiter
import io.github.oshai.kotlinlogging.KotlinLogging
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RateLimitCleanupJob(
    private val chatRateLimiter: RateLimiter,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "chatRateLimitCleanup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    fun cleanup() {
        try {
            val deleted = chatRateLimiter.cleanupExpired()
            if (deleted > 0) log.info { "ChatRateLimit cleaned expired rows: $deleted" }
        } catch (e: Exception) {
            log.error(e) { "ChatRateLimit cleanup failed" }
        }
    }
}
