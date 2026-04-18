package com.blog.ai.core.domain.crawl

import com.blog.ai.core.support.properties.SlackProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SlackNotifier(
    private val slackProperties: SlackProperties,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun send(message: String) {
        if (slackProperties.webhookUrl.isBlank()) return

        try {
            RestClient
                .create()
                .post()
                .uri(slackProperties.webhookUrl)
                .body(mapOf("text" to message))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.error(e) { "Slack notification failed" }
        }
    }
}
