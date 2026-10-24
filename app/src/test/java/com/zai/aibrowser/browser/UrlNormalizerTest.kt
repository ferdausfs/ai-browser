package com.zai.aibrowser.browser

import com.zai.aibrowser.privacy.SearchEngines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** URL normalization: search-vs-navigate decision and engine selection. */
class UrlNormalizerTest {

    private val ddg = SearchEngines.byId("duckduckgo")
    private val google = SearchEngines.byId("google")

    @Test
    fun `blank input is null`() {
        assertNull(UrlNormalizer.normalize("", ddg))
        assertNull(UrlNormalizer.normalize("   ", ddg))
    }

    @Test
    fun `full urls pass through unchanged`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("https://example.com", ddg))
        assertEquals(
            "http://example.org/page?x=1",
            UrlNormalizer.normalize("http://example.org/page?x=1", ddg)
        )
    }

    @Test
    fun `bare domains get https prefix`() {
        assertEquals("https://example.com", UrlNormalizer.normalize("example.com", ddg))
        assertEquals("https://sub.domain.org", UrlNormalizer.normalize("sub.domain.org", ddg))
        assertEquals("https://localhost:8080", UrlNormalizer.normalize("localhost:8080", ddg))
    }

    @Test
    fun `phrases with spaces are searches`() {
        assertEquals(
            "https://duckduckgo.com/?q=android+webview+privacy",
            UrlNormalizer.normalize("android webview privacy", ddg)
        )
    }

    @Test
    fun `query without dots is a search even if single word`() {
        assertEquals(
            "https://duckduckgo.com/?q=kotlin",
            UrlNormalizer.normalize("kotlin", ddg)
        )
    }

    @Test
    fun `search engine selection changes the template`() {
        assertEquals(
            "https://www.google.com/search?q=kotlin",
            UrlNormalizer.normalize("kotlin", google)
        )
    }

    @Test
    fun `about urls are neutralized to about blank`() {
        assertEquals("about:blank", UrlNormalizer.normalize("about:blank", ddg))
        assertEquals("about:blank", UrlNormalizer.normalize("about:config", ddg))
    }

    @Test
    fun `special characters are url-encoded`() {
        assertEquals(
            "https://duckduckgo.com/?q=cat+%26+dog",
            UrlNormalizer.normalize("cat & dog", ddg)
        )
    }
}
