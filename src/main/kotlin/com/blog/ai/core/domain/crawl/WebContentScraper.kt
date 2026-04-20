package com.blog.ai.core.domain.crawl

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component

@Component
class WebContentScraper(
    private val contentCleaner: ContentCleaner,
) {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val TIMEOUT_MS = 10_000

        private val CONTENT_SELECTORS =
            listOf(
                "article",
                "[role=main]",
                ".post-content",
                ".article-content",
                ".entry-content",
                ".blog-content",
                ".markdown-body",
                ".post-body",
                "main",
            )
    }

    fun scrape(url: String): String? =
        try {
            val doc = fetchDocument(url)
            extractContent(doc)
        } catch (e: Exception) {
            log.debug { "Web scrape failed: url=$url, reason=${e.message}" }
            null
        }

    private fun fetchDocument(url: String): Document =
        Jsoup
            .connect(url)
            .timeout(TIMEOUT_MS)
            .userAgent("Mozilla/5.0 (compatible; BlogAIBot/1.0)")
            .followRedirects(true)
            .get()

    private fun extractContent(doc: Document): String? {
        for (selector in CONTENT_SELECTORS) {
            val element = doc.selectFirst(selector) ?: continue
            val cleaned = contentCleaner.clean(element.html())
            if (!cleaned.isNullOrBlank()) return cleaned
        }
        val body = doc.body() ?: return null
        val bodyText = contentCleaner.clean(body.html())
        return bodyText?.takeIf { it.length > 200 }
    }
}
