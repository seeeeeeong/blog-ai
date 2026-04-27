package com.blog.ai.core.domain.crawl

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dankito.readability4j.Readability4J
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
        private const val MIN_USEFUL_BODY_LENGTH = 200

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
            extract(url, doc)
        } catch (e: Exception) {
            log.debug { "Web scrape failed: url=$url, reason=${e.message}" }
            null
        }

    internal fun extract(
        url: String,
        doc: Document,
    ): String? = extractWithReadability(url, doc) ?: extractWithSelectors(doc)

    private fun fetchDocument(url: String): Document =
        Jsoup
            .connect(url)
            .timeout(TIMEOUT_MS)
            .userAgent("Mozilla/5.0 (compatible; BlogAIBot/1.0)")
            .followRedirects(true)
            .get()

    private fun extractWithReadability(
        url: String,
        doc: Document,
    ): String? {
        val articleHtml =
            try {
                Readability4J(url, doc.outerHtml()).parse().content
            } catch (e: Exception) {
                log.debug { "Readability extraction failed: url=$url, reason=${e.message}" }
                null
            } ?: return null
        val cleaned = contentCleaner.clean(articleHtml) ?: return null
        return cleaned.takeIf { it.length >= MIN_USEFUL_BODY_LENGTH }
    }

    private fun extractWithSelectors(doc: Document): String? {
        for (selector in CONTENT_SELECTORS) {
            val element = doc.selectFirst(selector) ?: continue
            val cleaned = contentCleaner.clean(element.html()) ?: continue
            if (cleaned.isNotBlank()) return cleaned
        }
        val bodyText = contentCleaner.clean(doc.body().html())
        return bodyText?.takeIf { it.length >= MIN_USEFUL_BODY_LENGTH }
    }
}
