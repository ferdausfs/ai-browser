package com.zai.aibrowser.privacy

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Privacy-related user settings and counters, backed by SharedPreferences.
 *
 * Holds an in-memory mirror of the values the WebView request interceptor reads
 * on every network request, so the interceptor never touches disk on the hot path.
 * All values have privacy-protective defaults:
 * - tracking protection ON
 * - third-party cookies OFF
 * - Safe Browsing ON
 */
class PrivacyPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // In-memory mirrors (written on the UI thread by the ViewModel, read on
    // WebView IO threads by the interceptor).
    @Volatile var trackingProtectionEnabled: Boolean = prefs.getBoolean(KEY_TRACKING, true)
        private set

    @Volatile var thirdPartyCookiesAllowed: Boolean = prefs.getBoolean(KEY_THIRD_PARTY_COOKIES, false)
        private set

    @Volatile var safeBrowsingEnabled: Boolean = prefs.getBoolean(KEY_SAFE_BROWSING, true)
        private set

    @Volatile var searchEngineId: String =
        prefs.getString(KEY_SEARCH_ENGINE, SearchEngines.DEFAULT_ID) ?: SearchEngines.DEFAULT_ID
        private set

    // Snapshot of per-site "allow trackers" exceptions (site eTLD+1 strings).
    @Volatile private var exceptions: Set<String> =
        prefs.getStringSet(KEY_EXCEPTIONS, emptySet()) ?: emptySet()

    fun siteExceptionSet(): Set<String> = exceptions

    // ---------- Setters ----------

    fun setTrackingProtection(enabled: Boolean) {
        trackingProtectionEnabled = enabled
        prefs.edit().putBoolean(KEY_TRACKING, enabled).apply()
    }

    fun setThirdPartyCookies(allowed: Boolean) {
        thirdPartyCookiesAllowed = allowed
        prefs.edit().putBoolean(KEY_THIRD_PARTY_COOKIES, allowed).apply()
    }

    fun setSafeBrowsing(enabled: Boolean) {
        safeBrowsingEnabled = enabled
        prefs.edit().putBoolean(KEY_SAFE_BROWSING, enabled).apply()
    }

    fun setSearchEngine(id: String) {
        searchEngineId = id
        prefs.edit().putString(KEY_SEARCH_ENGINE, id).apply()
    }

    /** @param site eTLD+1 of the site for which trackers are allowed again. */
    fun addSiteException(site: String) {
        val updated = exceptions + site
        exceptions = updated
        prefs.edit().putStringSet(KEY_EXCEPTIONS, updated).apply()
    }

    fun removeSiteException(site: String) {
        val updated = exceptions - site
        exceptions = updated
        prefs.edit().putStringSet(KEY_EXCEPTIONS, updated).apply()
    }

    fun isSiteException(site: String): Boolean = site in exceptions

    // ---------- Blocked-request counters (real counts only) ----------

    /**
     * Counts one actually-blocked request. Persisted lazily: the hot counter is
     * in-memory; a single [flushCounters] write covers all increments since the
     * last flush (called when the dashboard opens or the app pauses).
     */
    private val dailyCount = java.util.concurrent.atomic.AtomicLong(0L)
    private val totalCount = java.util.concurrent.atomic.AtomicLong(
        prefs.getLong(KEY_TOTAL_BLOCKED, 0L)
    )
    private val dailyDate: String = today()

    init {
        // Reset the daily counter when the stored day differs from today.
        val storedDate = prefs.getString(KEY_DAILY_DATE, null)
        if (storedDate != dailyDate) {
            prefs.edit().putString(KEY_DAILY_DATE, dailyDate).putLong(KEY_DAILY_BLOCKED, 0L).apply()
        } else {
            dailyCount.set(prefs.getLong(KEY_DAILY_BLOCKED, 0L))
        }
    }

    fun recordBlocked() {
        dailyCount.incrementAndGet()
        totalCount.incrementAndGet()
    }

    /** Persists counter changes; cheap enough to call when the dashboard opens. */
    fun flushCounters() {
        prefs.edit()
            .putLong(KEY_DAILY_BLOCKED, dailyCount.get())
            .putLong(KEY_TOTAL_BLOCKED, totalCount.get())
            .putString(KEY_DAILY_DATE, dailyDate)
            .apply()
    }

    fun blockedToday(): Long = dailyCount.get()

    fun blockedTotal(): Long = totalCount.get()

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        const val PREFS_NAME = "privacy_prefs"
        private const val KEY_TRACKING = "tracking_protection"
        private const val KEY_THIRD_PARTY_COOKIES = "third_party_cookies"
        private const val KEY_SAFE_BROWSING = "safe_browsing"
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_EXCEPTIONS = "tracker_exceptions"
        private const val KEY_DAILY_BLOCKED = "blocked_today"
        private const val KEY_DAILY_DATE = "blocked_today_date"
        private const val KEY_TOTAL_BLOCKED = "blocked_total"
    }
}

/** Built-in search engines. DuckDuckGo is the privacy-first default. */
object SearchEngines {

    data class Engine(val id: String, val label: String, val queryTemplate: String)

    val ALL = listOf(
        Engine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
        Engine("startpage", "Startpage", "https://www.startpage.com/sp/search?query="),
        Engine("brave", "Brave Search", "https://search.brave.com/search?q="),
        Engine("google", "Google", "https://www.google.com/search?q="),
        Engine("bing", "Bing", "https://www.bing.com/search?q=")
    )

    const val DEFAULT_ID = "duckduckgo"

    fun byId(id: String): Engine = ALL.firstOrNull { it.id == id } ?: ALL.first()
}
