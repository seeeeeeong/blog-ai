package com.blog.ai.scheduler

import com.blog.ai.core.domain.article.ArticleEmbedService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EmbeddingRetryScheduler(
    private val articleEmbedService: ArticleEmbedService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3600000)
    fun retryFailed() {
        val cleared = articleEmbedService.clearAllErrors()
        if (cleared > 0) {
            log.info("임베딩 에러 초기화: {}건, 재시도 시작", cleared)
            articleEmbedService.embedPending()
        }
    }
}
