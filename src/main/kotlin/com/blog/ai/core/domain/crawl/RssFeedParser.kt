package com.blog.ai.core.domain.crawl

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.net.URI
import java.security.MessageDigest

@Component
class RssFeedParser(
    private val contentCleaner: ContentCleaner,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun parse(rssUrl: String): List<ParsedArticle> {
        return try {
            val feed = SyndFeedInput().build(XmlReader(URI(rssUrl).toURL()))
            feed.entries.mapNotNull { entry ->
                val url = entry.link ?: return@mapNotNull null
                val title = entry.title ?: return@mapNotNull null
                val rawContent =
                    entry.contents?.firstOrNull()?.value
                        ?: entry.description?.value
                ParsedArticle(
                    title = title.trim(),
                    url = url.trim(),
                    urlHash = sha256(url.trim()),
                    content = contentCleaner.clean(rawContent),
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
