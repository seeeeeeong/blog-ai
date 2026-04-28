package com.blog.ai.crawl.infrastructure.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentCleanerTest {
    private val cleaner = ContentCleaner()

    @Test
    fun `returns null for null or blank input`() {
        assertNull(cleaner.clean(null))
        assertNull(cleaner.clean(""))
        assertNull(cleaner.clean("   "))
        assertNull(cleaner.clean("<p></p>"))
    }

    @Test
    fun `strips html tags and collapses whitespace`() {
        val html = "<p>Hello</p>  <div>\n\tworld</div>"
        assertEquals("Hello world", cleaner.clean(html))
    }

    @Test
    fun `preserves long content beyond old 5000 char limit`() {
        val longText = "a".repeat(12_000)
        val html = "<p>$longText</p>"
        val cleaned = cleaner.clean(html)
        assertEquals(12_000, cleaned?.length)
    }

    @Test
    fun `truncates at defensive limit for abnormally large pages`() {
        val huge = "x".repeat(250_000)
        val cleaned = cleaner.clean("<p>$huge</p>")
        assertEquals(200_000, cleaned?.length)
        assertTrue(cleaned?.all { it == 'x' } == true)
    }

    @Test
    fun `handles korean content correctly`() {
        val html = "<article>쿠팡 엔지니어링 블로그에서 <b>pgvector</b>를 활용한 사례를 소개합니다.</article>"
        val cleaned = cleaner.clean(html)
        assertEquals("쿠팡 엔지니어링 블로그에서 pgvector를 활용한 사례를 소개합니다.", cleaned)
    }
}
