package com.blog.ai.core.domain.crawl

import com.blog.ai.core.support.properties.SlackProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SlackNotifier(
    private val slackProperties: SlackProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun send(message: String) {
        if (slackProperties.webhookUrl.isBlank()) return

        try {
            RestClient.create()
                .post()
                .uri(slackProperties.webhookUrl)
                .body(mapOf("text" to message))
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("Slack notification failed: {}", e.message)
        }
    }
}
