package com.blog.ai.core.domain.crawl

import com.blog.ai.core.support.properties.SlackProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SlackNotifier(
    private val slackProperties: SlackProperties,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 3_000
    }

    private val restClient: RestClient =
        RestClient
            .builder()
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                },
            ).build()

    fun send(message: String) {
        if (slackProperties.webhookUrl.isBlank()) return

        try {
            restClient
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
