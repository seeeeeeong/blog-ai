package com.blog.ai.core.domain.crawl

import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class ContentCleaner {

    companion object {
        private const val MAX_LENGTH = 5000
    }

    fun clean(html: String?): String? {
        if (html.isNullOrBlank()) return null

        val text = Jsoup.parse(html).text()
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.isBlank()) return null

        return if (text.length > MAX_LENGTH) text.substring(0, MAX_LENGTH) else text
    }
}
