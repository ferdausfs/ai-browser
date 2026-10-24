package com.zai.aibrowser.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the tracker-blocking matching logic. These verify the core
 * promises: domain-based matching (no naive substring), first-party exception,
 * per-site exceptions, and the kill switch.
 */
class TrackerBlockerTest {

    @Before
    fun setUp() {
        TrackerBlocker.loadForTest(
            setOf(
                "doubleclick.net",
                "google-analytics.com",
                "analytics.tiktok.com",
                "graph.facebook.com",
                "scorecardresearch.com"
            )
        )
    }

    // ---------- registrableDomain ----------

    @Test
    fun `registrable domain of simple host is last two labels`() {
        assertEquals("example.com", TrackerBlocker.registrableDomain("example.com"))
        assertEquals("example.com", TrackerBlocker.registrableDomain("www.example.com"))
        assertEquals("example.com", TrackerBlocker.registrableDomain("a.b.c.example.com"))
    }

    @Test
    fun `registrable domain handles two-part public suffixes`() {
        assertEquals("bbc.co.uk", TrackerBlocker.registrableDomain("www.bbc.co.uk"))
        assertEquals("example.com.bd", TrackerBlocker.registrableDomain("shop.example.com.bd"))
    }

    @Test
    fun `registrable domain passes through single labels and IPs`() {
        assertEquals("localhost", TrackerBlocker.registrableDomain("localhost"))
        assertEquals("192.168.1.4", TrackerBlocker.registrableDomain("192.168.1.4"))
        assertEquals(null, TrackerBlocker.registrableDomain(null))
        assertEquals(null, TrackerBlocker.registrableDomain(""))
    }

    @Test
    fun `registrable domain strips trailing dot`() {
        assertEquals("example.com", TrackerBlocker.registrableDomain("example.com."))
    }

    // ---------- isListed ----------

    @Test
    fun `exact host match is listed`() {
        assertTrue(TrackerBlocker.isListed("doubleclick.net"))
    }

    @Test
    fun `subdomain of listed host is listed via parent walk`() {
        assertTrue(TrackerBlocker.isListed("stats.g.doubleclick.net"))
    }

    @Test
    fun `unrelated host is not listed and substring match never fires`() {
        assertFalse(TrackerBlocker.isListed("doubleclick.net.evil.example.com"))
        assertFalse(TrackerBlocker.isListed("notdoubleclick.net"))
        assertFalse(TrackerBlocker.isListed("example.com"))
    }

    @Test
    fun `blank and malformed hosts are never listed`() {
        assertFalse(TrackerBlocker.isListed(null))
        assertFalse(TrackerBlocker.isListed(""))
        assertFalse(TrackerBlocker.isListed("   "))
    }

    // ---------- shouldBlock ----------

    @Test
    fun `third-party listed request is blocked`() {
        assertTrue(
            TrackerBlocker.shouldBlock(
                pageHost = "news.example.com",
                requestHost = "stats.g.doubleclick.net",
                protectionEnabled = true,
                siteExceptions = emptySet()
            )
        )
    }

    @Test
    fun `first-party request is never blocked even if listed`() {
        assertFalse(
            TrackerBlocker.shouldBlock(
                pageHost = "www.google-analytics.com",
                requestHost = "google-analytics.com",
                protectionEnabled = true,
                siteExceptions = emptySet()
            )
        )
    }

    @Test
    fun `third-party unlisted request passes`() {
        assertFalse(
            TrackerBlocker.shouldBlock(
                pageHost = "news.example.com",
                requestHost = "cdn.unrelated.com",
                protectionEnabled = true,
                siteExceptions = emptySet()
            )
        )
    }

    @Test
    fun `per-site exception disables blocking for that site only`() {
        // The dashboard stores exceptions as registrable domains (eTLD+1), so
        // every subdomain of the exempted site is covered.
        val exceptions = setOf("example.com")
        assertTrue(
            "still blocked on other sites",
            TrackerBlocker.shouldBlock(
                pageHost = "shop.other.org",
                requestHost = "stats.g.doubleclick.net",
                protectionEnabled = true,
                siteExceptions = exceptions
            )
        )
        assertFalse(
            "not blocked on the exempted site (any subdomain)",
            TrackerBlocker.shouldBlock(
                pageHost = "news.example.com",
                requestHost = "stats.g.doubleclick.net",
                protectionEnabled = true,
                siteExceptions = exceptions
            )
        )
    }

    @Test
    fun `kill switch disables all blocking`() {
        assertFalse(
            TrackerBlocker.shouldBlock(
                pageHost = "news.example.com",
                requestHost = "stats.g.doubleclick.net",
                protectionEnabled = false,
                siteExceptions = emptySet()
            )
        )
    }

    @Test
    fun `same registrable site via different subdomains is first-party`() {
        assertFalse(
            TrackerBlocker.shouldBlock(
                pageHost = "www.bbc.co.uk",
                requestHost = "static.bbc.co.uk",
                protectionEnabled = true,
                siteExceptions = emptySet()
            )
        )
    }

    @Test
    fun `page host of a listed tracker itself does not exempt unrelated third parties`() {
        // The page is a listed tracker; a request to ANOTHER listed tracker on
        // that page is still third-party and blocked.
        assertTrue(
            TrackerBlocker.shouldBlock(
                pageHost = "graph.facebook.com",
                requestHost = "scorecardresearch.com",
                protectionEnabled = true,
                siteExceptions = emptySet()
            )
        )
    }
}
