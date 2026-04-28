package com.blog.ai.crawl.infrastructure.parser

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class ContentCleaner {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val DEFENSIVE_MAX_LENGTH = 200_000
    }

    fun clean(html: String?): String? {
        if (html.isNullOrBlank()) return null

        val text =
            Jsoup
                .parse(html)
                .text()
                .replace(Regex("\\s+"), " ")
                .trim()

        if (text.isBlank()) return null

        if (text.length > DEFENSIVE_MAX_LENGTH) {
            log.warn { "Content exceeds defensive limit, truncating: length=${text.length}" }
            return text.substring(0, DEFENSIVE_MAX_LENGTH)
        }
        return text
    }
}
