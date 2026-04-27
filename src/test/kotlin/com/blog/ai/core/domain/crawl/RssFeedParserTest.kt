package com.blog.ai.core.domain.crawl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.io.File

class RssFeedParserTest {
    private val contentCleaner = ContentCleaner()
    private val webContentScraper: WebContentScraper =
        Mockito.mock(WebContentScraper::class.java).also {
            Mockito.`when`(it.scrape(anyString())).thenReturn(null)
        }
    private val parser = RssFeedParser(contentCleaner, webContentScraper)

    private fun trustedBody(): String = "lorem ipsum ".repeat(60)

    @Test
    fun `parses valid RSS feed and extracts entries`() {
        val xml = wrapFeed(entry("Hello", "https://example.com/a", "<p>Body</p>"))
        val articles = parseFromTempFile(xml)

        assertEquals(1, articles.size)
        assertEquals("Hello", articles[0].title)
        assertEquals("https://example.com/a", articles[0].url)
        assertEquals("Body", articles[0].content)
    }

    @Test
    fun `recovers from invalid XML control chars by sanitizing and retrying`() {
        val polluted =
            wrapFeed(
                entry("Item with  ctrl", "https://example.com/b", "Body  with  bell"),
            )
        val articles = parseFromTempFile(polluted)

        assertEquals(1, articles.size)
        assertTrue(articles[0].title.contains("ctrl"))
        assertEquals("https://example.com/b", articles[0].url)
    }

    @Test
    fun `returns empty list when feed is malformed beyond control char issue`() {
        val articles = parseFromTempFile("<<not xml>>")
        assertTrue(articles.isEmpty())
    }

    @Test
    fun `returns empty list when fetch fails`() {
        val articles = parser.parse("file:///does/not/exist.xml")
        assertTrue(articles.isEmpty())
    }

    @Test
    fun `uses trusted rss body without calling scraper when body is long enough`() {
        val body = trustedBody()
        val xml = wrapFeed(entry("Long", "https://example.com/long", body))
        val scraper = Mockito.mock(WebContentScraper::class.java)
        val parser = RssFeedParser(contentCleaner, scraper)

        val temp = File.createTempFile("rss-test-", ".xml")
        try {
            temp.writeText(xml, Charsets.UTF_8)
            val articles = parser.parse(temp.toURI().toString())
            assertEquals(1, articles.size)
            assertEquals(body.trim(), articles[0].content)
            Mockito.verify(scraper, Mockito.never()).scrape(anyString())
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `falls back to scraper when rss body is below threshold`() {
        val scraped = "scraped article body ".repeat(40)
        val scraper =
            Mockito.mock(WebContentScraper::class.java).also {
                Mockito.`when`(it.scrape(anyString())).thenReturn(scraped)
            }
        val parser = RssFeedParser(contentCleaner, scraper)
        val xml = wrapFeed(entry("Short", "https://example.com/short", "<p>tiny</p>"))

        val temp = File.createTempFile("rss-test-", ".xml")
        try {
            temp.writeText(xml, Charsets.UTF_8)
            val articles = parser.parse(temp.toURI().toString())
            assertEquals(1, articles.size)
            assertEquals(scraped, articles[0].content)
            Mockito.verify(scraper).scrape("https://example.com/short")
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `keeps short rss body when scraper also fails`() {
        val xml = wrapFeed(entry("Short", "https://example.com/short", "<p>tiny</p>"))
        val articles = parseFromTempFile(xml)
        assertEquals(1, articles.size)
        assertEquals("tiny", articles[0].content)
    }

    private fun parseFromTempFile(xml: String): List<ParsedArticle> {
        val temp = File.createTempFile("rss-test-", ".xml")
        try {
            temp.writeText(xml, Charsets.UTF_8)
            return parser.parse(temp.toURI().toString())
        } finally {
            temp.delete()
        }
    }

    private fun wrapFeed(vararg entries: String): String =
        """<?xml version="1.0" encoding="UTF-8"?>
        |<rss version="2.0">
        |  <channel>
        |    <title>Test</title>
        |    <link>https://example.com</link>
        |    <description>Test feed</description>
        |    ${entries.joinToString("\n")}
        |  </channel>
        |</rss>
        """.trimMargin()

    private fun entry(
        title: String,
        link: String,
        content: String,
    ): String =
        """
        <item>
          <title>$title</title>
          <link>$link</link>
          <description><![CDATA[$content]]></description>
        </item>
        """.trimIndent()
}
