package com.zai.aibrowser.privacy

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Request-based tracker blocking for Android WebView.
 *
 * How it works
 * ------------
 * WebView exposes every resource request to [android.webkit.WebViewClient.shouldInterceptRequest].
 * For each third-party subresource request, the request host is matched against a
 * curated blocklist (StevenBlack/hosts unified base file, MIT license, embedded as
 * a build-time asset). Matched requests receive an empty response body.
 *
 * Matching rules (deliberately NOT naive substring blocking):
 * - Hostnames are compared domain-by-domain, walking up the parent chain:
 *   "ads.example.co.uk" tries "ads.example.co.uk", "example.co.uk" (never "co.uk"
 *   alone) against the blocklist.
 * - First-party requests are never blocked: if the request's site (approximated
 *   eTLD+1) equals the current page's site, the request passes. This keeps
 *   deliberate visits (and CDNs shared with the page itself) working.
 * - Main-frame navigations are never blocked (enforced at the call site).
 * - Per-site exceptions: a site added to the exception list has tracking
 *   protection fully disabled for it.
 *
 * Known limitations (documented, not hidden):
 * - WebView cannot see every tracking channel. Service workers, WebRTC, DNS
 *   resolution, and some prefetch/preconnect calls bypass the interceptor, so
 *   blocked counts are floor counts, not totals.
 * - The embedded eTLD+1 approximation uses a small list of common two-part
 *   public suffixes; exotic ones may misclassify an edge case as first-party
 *   (fail-open for that host only).
 * - The list is a snapshot of a community-maintained source. It must be
 *   refreshed periodically (scripts/gen_tracker_list.py) and does not protect
 *   against first-party tracking or fingerprinting.
 * - This is a baseline privacy feature, NOT complete anti-tracking protection.
 */
object TrackerBlocker {

    private const val ASSET_PATH = "tracker_domains.txt"
    private val loaded = AtomicBoolean(false)
    private var blocklist: Set<String> = emptySet()

    /** Loads the embedded blocklist once; subsequent calls are no-ops. */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded.get()) return
        blocklist = try {
            context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line ->
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("#")) null else t
                }.toSet()
            }
        } catch (_: Exception) {
            // Fail open: if the asset is somehow unreadable, never block.
            emptySet()
        }
        loaded.set(true)
    }

    fun isLoaded(): Boolean = loaded.get()

    fun listSize(): Int = blocklist.size

    /** Test-only: injects a list without touching assets. */
    internal fun loadForTest(hosts: Set<String>) {
        blocklist = hosts
        loaded.set(true)
    }

    // Common two-part public suffixes used for the eTLD+1 approximation.
    private val MULTI_PART_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "co.jp", "ne.jp", "or.jp", "ac.jp",
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "com.br", "net.br",
        "org.br", "com.tr", "net.tr", "org.tr", "co.in", "net.in", "org.in",
        "com.bd", "net.bd", "org.bd", "co.nz", "net.nz", "org.nz", "com.sg",
        "com.my", "co.za", "org.za", "com.ph", "com.hk", "com.tw", "co.kr",
        "or.id", "co.id", "co.th", "com.vn", "com.mx", "com.ar", "com.cn",
        "co.il", "com.pk", "com.eg", "com.sa", "com.ng", "com.gh", "com.et"
    )

    /**
     * Approximates the registrable domain (eTLD+1) of [host].
     * Returns null for IP literals, single-label hosts, or empty input.
     */
    fun registrableDomain(host: String?): String? {
        if (host.isNullOrBlank()) return null
        val h = host.trim().lowercase().rstrip('.')
        if (h.isEmpty()) return null
        // IPv4 / IPv6 literals and single-label hosts: treat as their own site.
        if (h.firstOrNull { it == ':' } != null) return h // bare IPv6
        val labels = h.split('.')
        if (labels.size == 1) return h
        // Dotted-quad IPv4 literal — never apply public-suffix logic.
        if (labels.size == 4 && labels.all { it.isNotEmpty() && it.all { c -> c.isDigit() } }) {
            return h
        }
        if (labels.size == 2) return h
        val lastTwo = labels[labels.size - 2] + "." + labels[labels.size - 1]
        if (lastTwo in MULTI_PART_SUFFIXES) {
            // Need one more label for the registrable part.
            return labels[labels.size - 3] + "." + lastTwo
        }
        return lastTwo
    }

    private fun String.rstrip(c: Char): String =
        if (endsWith(c)) dropLast(1) else this

    /**
     * True when [host] (or any of its parents) is on the blocklist.
     * Pure function over the loaded list — safe to call from any thread.
     */
    fun isListed(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.trim().lowercase().rstrip('.')
        if (h.isEmpty()) return false
        var candidate = h
        while (true) {
            if (candidate in blocklist) return true
            val dot = candidate.indexOf('.')
            if (dot <= 0 || dot == candidate.length - 1) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    /**
     * The blocking decision for one subresource request.
     *
     * @param pageHost   host of the page that triggered the request (from the WebView)
     * @param requestHost host of the requested resource
     * @param protectionEnabled global tracking-protection switch
     * @param siteExceptions per-site exceptions (eTLD+1 strings with trackers allowed)
     * @return true when the request should receive an empty response
     */
    fun shouldBlock(
        pageHost: String?,
        requestHost: String,
        protectionEnabled: Boolean,
        siteExceptions: Set<String>
    ): Boolean {
        if (!protectionEnabled) return false
        val reqSite = registrableDomain(requestHost) ?: return false
        val pageSite = registrableDomain(pageHost)
        // First-party requests always pass.
        if (pageSite != null && pageSite == reqSite) return false
        // Per-site exception: trackers allowed on this page's site.
        if (pageSite != null && pageSite in siteExceptions) return false
        return isListed(requestHost)
    }

    /** Standard empty response used to terminate a blocked request. */
    fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
