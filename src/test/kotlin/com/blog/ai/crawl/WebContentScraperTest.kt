package com.blog.ai.crawl

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebContentScraperTest {
    private val scraper = WebContentScraper(ContentCleaner())
    private val baseUrl = "https://example.com/post"

    @Test
    fun `extracts main article body with readability and drops chrome boilerplate`() {
        val html = articlePageWithLongBody()

        val content = requireNotNull(scraper.extract(baseUrl, Jsoup.parse(html, baseUrl)))

        assertTrue(content.contains("P95 cuts through"))
        assertTrue(content.length > 500)
        assertFalse(content.contains("All rights reserved"))
        assertFalse(content.contains("Pricing"))
    }

    @Test
    fun `falls back to css selector when readability returns nothing useful`() {
        val articleBody = "selector body keeps working ".repeat(40)
        val html = articlePageWithRawArticle(articleBody)

        val content = requireNotNull(scraper.extract(baseUrl, Jsoup.parse(html, baseUrl)))

        assertTrue(content.contains("selector body"))
    }

    @Test
    fun `returns null when fetch fails`() {
        assertNull(scraper.scrape("file:///does/not/exist.html"))
    }

    private fun articlePageWithLongBody(): String {
        val body = LONG_BODY_PARAGRAPHS.joinToString("\n") { "<p>$it</p>" }
        return """<!DOCTYPE html>
            |<html>
            |  <head><title>Latency primer</title></head>
            |  <body>
            |    <header><nav>$NAV_LINKS</nav></header>
            |    <main>
            |      <article>
            |        <h1>Why P95 beats average latency</h1>
            |        $body
            |      </article>
            |    </main>
            |    <footer>$FOOTER_TEXT</footer>
            |  </body>
            |</html>
            """.trimMargin()
    }

    private fun articlePageWithRawArticle(body: String): String =
        """<!DOCTYPE html>
        |<html>
        |  <head><title>Tiny</title></head>
        |  <body>
        |    <article>$body</article>
        |  </body>
        |</html>
        """.trimMargin()

    companion object {
        private const val FOOTER_TEXT = "© 2026 Example Co. All rights reserved. Privacy. Terms."
        private val MENU_ITEMS = listOf("Home", "About", "Blog", "Contact", "Pricing", "Login")
        private val NAV_LINKS = MENU_ITEMS.joinToString("") { "<a href=\"#\">$it</a>" }
        private val LONG_BODY_PARAGRAPHS =
            listOf(
                "Average latency is easy to measure and easy to misread. ".repeat(20),
                "P95 cuts through the noise by anchoring on the slow tail. ".repeat(20),
                "User-perceived latency is what moves retention. ".repeat(20),
            )
    }
}
