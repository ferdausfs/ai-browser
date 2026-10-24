package com.zai.aibrowser.browser

import com.zai.aibrowser.privacy.SearchEngines
import java.net.URLEncoder

/**
 * Converts whatever the user typed into a loadable URL, or null if empty.
 * This is the only place that decides "search vs navigate", so it stays auditable.
 * The search engine is user-selectable (privacy-first default: DuckDuckGo).
 */
object UrlNormalizer {

    const val LEGACY_GOOGLE_TEMPLATE = "https://www.google.com/search?q="

    fun normalize(input: String, engine: SearchEngines.Engine = SearchEngines.byId(SearchEngines.DEFAULT_ID)): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return trimmed
        if (lower.startsWith("about:")) return "about:blank"
        val looksLikeUrl = !trimmed.contains(' ') &&
            (trimmed.contains('.') || lower.startsWith("localhost"))
        return if (looksLikeUrl) {
            "https://$trimmed"
        } else {
            engine.queryTemplate + URLEncoder.encode(trimmed, "UTF-8")
        }
    }
}
