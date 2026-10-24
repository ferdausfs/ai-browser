package com.zai.aibrowser.browser

import java.net.URLEncoder

/**
 * Converts whatever the user typed into a loadable URL, or null if empty.
 * This is the only place that decides "search vs navigate", so it stays auditable.
 */
object UrlNormalizer {

    const val SEARCH_TEMPLATE = "https://www.google.com/search?q="

    fun normalize(input: String): String? {
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
            SEARCH_TEMPLATE + URLEncoder.encode(trimmed, "UTF-8")
        }
    }
}
