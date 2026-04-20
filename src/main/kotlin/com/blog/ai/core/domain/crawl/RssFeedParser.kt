package com.blog.ai.core.domain.crawl

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.io.StringReader
import java.net.URI
import java.security.MessageDigest

@Component
class RssFeedParser(
    private val contentCleaner: ContentCleaner,
    private val webContentScraper: WebContentScraper,
) {
    companion object {
        private val log = KotlinLogging.logger {}

        private val INVALID_XML_CHARS = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")

        private fun sanitizeXml(raw: String): String = INVALID_XML_CHARS.replace(raw, "")
    }

    fun parse(rssUrl: String): List<ParsedArticle> {
        return try {
            val feed =
                try {
                    SyndFeedInput().build(XmlReader(URI(rssUrl).toURL()))
                } catch (e: com.rometools.rome.io.ParsingFeedException) {
                    log.info { "Retrying RSS parse after sanitizing invalid XML chars: url=$rssUrl" }
                    val raw = URI(rssUrl).toURL().readText()
                    SyndFeedInput().build(StringReader(sanitizeXml(raw)))
                }
            feed.entries.mapNotNull { entry ->
                val url = entry.link ?: return@mapNotNull null
                val title = entry.title ?: return@mapNotNull null
                val rawContent =
                    entry.contents?.firstOrNull()?.value
                        ?: entry.description?.value
                val rssContent = contentCleaner.clean(rawContent)
                val content = rssContent ?: webContentScraper.scrape(url.trim())
                ParsedArticle(
                    title = title.trim(),
                    url = url.trim(),
                    urlHash = sha256(url.trim()),
                    content = content,
                    publishedAt = entry.publishedDate?.toInstant(),
                )
            }
        } catch (e: Exception) {
            log.warn(e) { "RSS parse failed: url=$rssUrl" }
            emptyList()
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
