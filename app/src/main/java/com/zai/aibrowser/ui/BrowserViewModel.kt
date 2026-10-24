package com.zai.aibrowser.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zai.aibrowser.ai.MockAgentController
import com.zai.aibrowser.ai.MockAiProvider
import com.zai.aibrowser.browser.BrowserTab
import com.zai.aibrowser.browser.SecureWebViewFactory
import com.zai.aibrowser.browser.TabManager
import com.zai.aibrowser.browser.UrlNormalizer
import com.zai.aibrowser.data.BrowserRepository
import com.zai.aibrowser.data.db.AppDatabase
import com.zai.aibrowser.data.db.BookmarkEntity
import com.zai.aibrowser.data.db.HistoryEntity
import com.zai.aibrowser.privacy.PrivacyPrefs
import com.zai.aibrowser.privacy.SearchEngines
import com.zai.aibrowser.privacy.TrackerBlocker
import com.zai.aibrowser.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns all browser state: tabs, address bar, bookmarks, history, sheets, theme,
 * privacy settings and the AI task panel (development preview with the on-device
 * mock provider — clearly disclosed, never simulated as a real backend).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = BrowserRepository(AppDatabase.get(app))
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val privacyPrefs = PrivacyPrefs(app)

    // ---------- Theme ----------

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private fun loadThemeMode(): ThemeMode =
        runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

    // ---------- Top-level screens & sheets ----------

    enum class Screen { BROWSER, SETTINGS }
    enum class Sheet { NONE, TABS, BOOKMARKS, HISTORY, MENU, AI, PRIVACY }

    private val _screen = MutableStateFlow(Screen.BROWSER)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _activeSheet = MutableStateFlow(Sheet.NONE)
    val activeSheet: StateFlow<Sheet> = _activeSheet.asStateFlow()

    fun openSettings() {
        _activeSheet.value = Sheet.NONE
        _screen.value = Screen.SETTINGS
    }

    fun closeSettings() {
        _screen.value = Screen.BROWSER
    }

    fun openSheet(sheet: Sheet) {
        if (sheet == Sheet.PRIVACY) refreshPrivacyStats()
        _activeSheet.value = sheet
    }

    fun closeSheet() {
        _activeSheet.value = Sheet.NONE
    }

    fun openBrowserAndNavigate(url: String) {
        _screen.value = Screen.BROWSER
        _activeSheet.value = Sheet.NONE
        navigateTo(url)
    }

    // ---------- Snackbar ----------

    private val _snackbarEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private fun notify(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    // ---------- Tabs ----------

    val tabManager = TabManager()
    val tabs: StateFlow<List<BrowserTab>> = tabManager.tabs
    val activeTabId: StateFlow<Long?> = tabManager.activeTabId

    val activeTab: StateFlow<BrowserTab?> = combine(tabManager.tabs, tabManager.activeTabId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---------- Address bar ----------

    val addressInput = MutableStateFlow("")
    private val _isEditingAddress = MutableStateFlow(false)
    val isEditingAddress: StateFlow<Boolean> = _isEditingAddress.asStateFlow()

    // ---------- Bookmarks & history ----------

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<HistoryEntity>> = repository.observeHistory(HISTORY_LIMIT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isBookmarked: StateFlow<Boolean> = activeTab.flatMapLatest { tab ->
        if (tab == null) flowOf(false)
        else tab.currentUrl.flatMapLatest { repository.observeIsBookmarked(it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val suggestions: StateFlow<List<Suggestion>> = _isEditingAddress.flatMapLatest { editing ->
        if (!editing) flowOf(emptyList())
        else addressInput.flatMapLatest { query ->
            combine(bookmarks, history) { bm, hs -> buildSuggestions(query, bm, hs) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- Privacy settings & live stats ----------

    private val _searchEngineId = MutableStateFlow(privacyPrefs.searchEngineId)
    val searchEngineId: StateFlow<String> = _searchEngineId.asStateFlow()

    private val _trackingProtection = MutableStateFlow(privacyPrefs.trackingProtectionEnabled)
    val trackingProtection: StateFlow<Boolean> = _trackingProtection.asStateFlow()

    private val _thirdPartyCookies = MutableStateFlow(privacyPrefs.thirdPartyCookiesAllowed)
    val thirdPartyCookies: StateFlow<Boolean> = _thirdPartyCookies.asStateFlow()

    private val _safeBrowsing = MutableStateFlow(privacyPrefs.safeBrowsingEnabled)
    val safeBrowsing: StateFlow<Boolean> = _safeBrowsing.asStateFlow()

    private val _siteExceptions = MutableStateFlow(privacyPrefs.siteExceptionSet())
    val siteExceptions: StateFlow<Set<String>> = _siteExceptions.asStateFlow()

    data class PrivacyStats(val blockedToday: Long, val blockedTotal: Long, val listSize: Int)

    private val _privacyStats = MutableStateFlow(
        PrivacyStats(privacyPrefs.blockedToday(), privacyPrefs.blockedTotal(), 0)
    )
    val privacyStats: StateFlow<PrivacyStats> = _privacyStats.asStateFlow()

    /** Reads fresh counters (and persists them) whenever the dashboard opens. */
    fun refreshPrivacyStats() {
        privacyPrefs.flushCounters()
        TrackerBlocker.ensureLoaded(getApplication())
        _privacyStats.value = PrivacyStats(
            privacyPrefs.blockedToday(),
            privacyPrefs.blockedTotal(),
            TrackerBlocker.listSize()
        )
    }

    fun setTrackingProtection(enabled: Boolean) {
        privacyPrefs.setTrackingProtection(enabled)
        _trackingProtection.value = enabled
    }

    fun setThirdPartyCookies(allowed: Boolean) {
        privacyPrefs.setThirdPartyCookies(allowed)
        _thirdPartyCookies.value = allowed
        val cm = CookieManager.getInstance()
        tabManager.tabs.value.forEach { tab -> tab.webView?.let { cm.setAcceptThirdPartyCookies(it, allowed) } }
    }

    fun setSafeBrowsing(enabled: Boolean) {
        privacyPrefs.setSafeBrowsing(enabled)
        _safeBrowsing.value = enabled
        tabManager.tabs.value.forEach { tab -> tab.webView?.settings?.safeBrowsingEnabled = enabled }
    }

    fun setSearchEngine(id: String) {
        privacyPrefs.setSearchEngine(id)
        _searchEngineId.value = id
    }

    /** Toggles "allow trackers on this site" for the given registrable domain. */
    fun toggleTrackerException(site: String) {
        if (site.isBlank()) return
        if (privacyPrefs.isSiteException(site)) {
            privacyPrefs.removeSiteException(site)
        } else {
            privacyPrefs.addSiteException(site)
        }
        _siteExceptions.value = privacyPrefs.siteExceptionSet()
    }

    // ---------- AI task panel (development preview — no real backend) ----------

    enum class AgentStepState { PENDING, ACTIVE, DONE, FAILED }
    data class AgentStep(val label: String, val state: AgentStepState)

    enum class AgentItemKind { SUGGESTED, EXECUTED, SYSTEM }
    data class AgentHistoryItem(val kind: AgentItemKind, val text: String)

    data class AgentUiState(
        val running: Boolean = false,
        val steps: List<AgentStep> = defaultSteps(),
        val history: List<AgentHistoryItem> = emptyList()
    ) {
        companion object {
            /** Progress states are part of the stable agent contract; with the
             *  development-only mock backend they stay PENDING and are labeled as
             *  unavailable instead of being faked. */
            fun defaultSteps() = listOf(
                AgentStep("Connecting to AI provider", AgentStepState.PENDING),
                AgentStep("Reading page", AgentStepState.PENDING),
                AgentStep("Finding element", AgentStepState.PENDING),
                AgentStep("Taking action", AgentStepState.PENDING),
                AgentStep("Waiting for approval", AgentStepState.PENDING)
            )
        }
    }

    private val _agentState = MutableStateFlow(AgentUiState())
    val agentState: StateFlow<AgentUiState> = _agentState.asStateFlow()

    val aiInput = MutableStateFlow("")
    private val agentController = MockAgentController(MockAiProvider())
    private var agentJob: Job? = null

    fun onAiInputChanged(text: String) {
        aiInput.value = text
    }

    /**
     * Runs a task against the on-device MOCK provider. This is a development
     * preview: nothing is executed, no page content is collected, and no data
     * leaves the device. The panel states this explicitly instead of simulating
     * progress of a real agent.
     */
    fun sendAiTask() {
        val task = aiInput.value.trim()
        if (task.isEmpty() || _agentState.value.running) return
        aiInput.value = ""
        agentJob = viewModelScope.launch {
            _agentState.value = AgentUiState(
                running = true,
                steps = listOf(
                    AgentStep("Connecting to AI provider", AgentStepState.ACTIVE),
                    AgentStep("Reading page", AgentStepState.PENDING),
                    AgentStep("Finding element", AgentStepState.PENDING),
                    AgentStep("Taking action", AgentStepState.PENDING),
                    AgentStep("Waiting for approval", AgentStepState.PENDING)
                ),
                history = _agentState.value.history + AgentHistoryItem(
                    AgentItemKind.SYSTEM, "Task started: \"$task\""
                )
            )
            delay(600) // simulate provider contact only; clearly reported as failed
            val reply = agentController.respondToCommand(task)
            _agentState.value = _agentState.value.copy(
                running = false,
                steps = listOf(
                    AgentStep("Connecting to AI provider", AgentStepState.FAILED),
                    AgentStep("Reading page", AgentStepState.PENDING),
                    AgentStep("Finding element", AgentStepState.PENDING),
                    AgentStep("Taking action", AgentStepState.PENDING),
                    AgentStep("Waiting for approval", AgentStepState.PENDING)
                ),
                history = _agentState.value.history + AgentHistoryItem(
                    AgentItemKind.SUGGESTED, "ask_user (suggested, not executed): $reply"
                )
            )
        }
    }

    /** Stop button: cancels the in-flight run for real; nothing fake is reported. */
    fun stopAgentRun() {
        agentJob?.cancel()
        agentJob = null
        if (_agentState.value.running) {
            _agentState.value = _agentState.value.copy(
                running = false,
                history = _agentState.value.history +
                    AgentHistoryItem(AgentItemKind.SYSTEM, "Run stopped by user. No actions were executed.")
            )
        }
    }

    /** Take Control: stops the run and hands the session back to the user. */
    fun takeControl() {
        stopAgentRun()
        closeSheet()
    }

    // ---------- WebView factory wiring ----------

    private val webViewFactory = SecureWebViewFactory(
        privacyPrefs = privacyPrefs,
        callbacks = SecureWebViewFactory.Callbacks(
            onVisited = { tabId, url, title ->
                viewModelScope.launch { recordVisit(tabId, url, title) }
            },
            onPersistTab = { tabId, url, title ->
                viewModelScope.launch { repository.updateNavigation(tabId, url, title) }
            },
            onExternalLinkBlocked = { notify("External app links are disabled for safety") }
        )
    )

    init {
        viewModelScope.launch { restoreTabs() }
        // Load the tracker blocklist off the main thread, once.
        viewModelScope.launch(Dispatchers.IO) {
            TrackerBlocker.ensureLoaded(getApplication())
        }
    }

    private suspend fun restoreTabs() {
        val saved = repository.getTabs()
        if (saved.isEmpty()) {
            val id = repository.addTab(
                url = "about:blank", title = "New tab", position = 0, isActive = true
            )
            tabManager.setTabs(listOf(BrowserTab(id, "about:blank")), id)
        } else {
            val restored = saved.map { BrowserTab(it.id, it.url) }
            tabManager.setTabs(
                restored,
                saved.firstOrNull { it.isActive }?.id ?: saved.first().id
            )
        }
        syncAddressInput()
    }

    // ---------- WebView lifecycle ----------

    fun obtainWebView(tab: BrowserTab, context: Context): android.webkit.WebView {
        tab.webView?.let { return it }
        val webView = webViewFactory.create(tab, context)
        tab.webView = webView
        val target = tab.currentUrl.value
        if (tab.hasNavigated.value && target.isNotBlank() && target != "about:blank") {
            webView.loadUrl(target)
        }
        return webView
    }

    fun onHostPause() {
        activeTab.value?.webView?.onPause()
        privacyPrefs.flushCounters()
    }

    fun onHostResume() {
        activeTab.value?.webView?.onResume()
    }

    /** True when the device currently has an usable data network. */
    fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ---------- Address bar commands ----------

    fun onAddressChanged(text: String) {
        addressInput.value = text
    }

    fun onAddressEditing(editing: Boolean) {
        _isEditingAddress.value = editing
        if (editing) {
            val url = activeTab.value?.currentUrl?.value.orEmpty()
            addressInput.value = if (url == "about:blank") "" else url
        } else {
            syncAddressInput()
        }
    }

    fun commitAddress(raw: String) {
        val engine = SearchEngines.byId(_searchEngineId.value)
        val url = UrlNormalizer.normalize(raw, engine) ?: return
        _isEditingAddress.value = false
        navigateTo(url)
    }

    fun openSuggestion(url: String) {
        _isEditingAddress.value = false
        navigateTo(url)
    }

    private fun syncAddressInput() {
        val url = activeTab.value?.currentUrl?.value.orEmpty()
        addressInput.value = if (url == "about:blank") "" else url
    }

    // ---------- Navigation commands ----------

    fun navigateTo(url: String) {
        val tab = activeTab.value ?: return
        tab.hasNavigated.value = true
        tab.error.value = null
        tab.currentUrl.value = url
        addressInput.value = if (url == "about:blank") "" else url
        tab.webView?.loadUrl(url)
        // If the WebView does not exist yet, obtainWebView() loads currentUrl
        // as soon as the composition creates it.
    }

    fun goBack() {
        activeTab.value?.webView?.goBack()
    }

    fun goForward() {
        activeTab.value?.webView?.goForward()
    }

    fun reload() {
        activeTab.value?.webView?.reload()
    }

    fun stop() {
        val tab = activeTab.value ?: return
        tab.webView?.stopLoading()
        tab.isLoading.value = false
        tab.progress.value = 100
    }

    fun retryCurrentTab() {
        val tab = activeTab.value ?: return
        tab.error.value = null
        val webView = tab.webView
        if (webView != null) {
            webView.reload()
        } else {
            tab.webViewEpoch.value += 1
            navigateTo(tab.currentUrl.value)
        }
    }

    fun goHome() {
        val tab = activeTab.value ?: return
        tab.hasNavigated.value = false
        tab.currentUrl.value = "about:blank"
        tab.error.value = null
        tab.webView?.loadUrl("about:blank")
        syncAddressInput()
        viewModelScope.launch {
            repository.updateNavigation(tab.id, "about:blank", tab.title.value)
        }
    }

    // ---------- Tab management ----------

    fun newTab() {
        viewModelScope.launch {
            val id = repository.addTab(
                url = "about:blank",
                title = "New tab",
                position = tabManager.tabs.value.size,
                isActive = true
            )
            tabManager.addTab(BrowserTab(id, "about:blank"))
            _activeSheet.value = Sheet.NONE
            syncAddressInput()
        }
    }

    fun closeTab(id: Long) {
        viewModelScope.launch {
            val removed = tabManager.removeTab(id) ?: return@launch
            repository.deleteTab(id)
            removed.webView?.let { webView ->
                removed.webView = null
                webView.post {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
            if (tabManager.tabs.value.isEmpty()) {
                val newId = repository.addTab(
                    url = "about:blank", title = "New tab", position = 0, isActive = true
                )
                tabManager.setTabs(listOf(BrowserTab(newId, "about:blank")), newId)
            }
            syncAddressInput()
        }
    }

    fun activateTab(id: Long) {
        val current = activeTab.value
        if (current?.id != id) current?.webView?.onPause()
        tabManager.activate(id)
        viewModelScope.launch { repository.setActiveTab(id) }
        _activeSheet.value = Sheet.NONE
        syncAddressInput()
    }

    // ---------- Bookmarks ----------

    fun toggleBookmark() {
        viewModelScope.launch {
            val tab = activeTab.value ?: return@launch
            val url = tab.currentUrl.value
            if (url.isBlank() || url == "about:blank") return@launch
            if (repository.isBookmarked(url)) {
                repository.removeBookmark(url)
                notify("Removed from bookmarks")
            } else {
                repository.addBookmark(url, tab.title.value.ifBlank { url })
                notify("Bookmarked")
            }
        }
    }

    fun deleteBookmark(url: String) {
        viewModelScope.launch {
            repository.removeBookmark(url)
            notify("Bookmark removed")
        }
    }

    // ---------- Privacy: clear browsing data ----------
    // Scope notes are surfaced in the dialog; they describe what the engine can
    // actually do. Cookies/site data and cache clearing are GLOBAL in WebView —
    // the dialog must never claim per-site scope. Bookmarks are preserved unless
    // explicitly selected (not offered here at all).

    /** Clears only the browsing history (device-local record). */
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            tabManager.tabs.value.forEach { it.lastRecordedUrl = null }
            notify("History cleared")
        }
    }

    fun clearBrowsingData(
        clearTabs: Boolean,
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean
    ) {
        viewModelScope.launch {
            if (clearTabs) {
                // Destroy every live WebView first, then wipe the tab registry.
                tabManager.tabs.value.forEach { tab ->
                    tab.webView?.let { wv ->
                        tab.webView = null
                        wv.post {
                            wv.stopLoading()
                            wv.destroy()
                        }
                    }
                }
                repository.clearTabs()
                val newId = repository.addTab(
                    url = "about:blank", title = "New tab", position = 0, isActive = true
                )
                tabManager.setTabs(listOf(BrowserTab(newId, "about:blank")), newId)
            }
            if (clearHistory) {
                repository.clearHistory()
                tabManager.tabs.value.forEach { it.lastRecordedUrl = null }
            }
            if (clearCookies) {
                // Covers cookies for ALL sites (WebView limitation, stated in UI).
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                // localStorage/sessionStorage/IndexedDB-style site data.
                WebStorage.getInstance().deleteAllData()
            }
            if (clearCache) {
                // Clears the whole WebView cache, not just the current site.
                tabManager.tabs.value.forEach { it.webView?.clearCache(true) }
            }
            val parts = buildList {
                if (clearTabs) add("tabs")
                if (clearHistory) add("history")
                if (clearCookies) add("cookies & site data")
                if (clearCache) add("cache")
            }
            notify(
                if (parts.isEmpty()) "Nothing selected"
                else "Cleared: " + parts.joinToString(", ")
            )
        }
    }

    private suspend fun recordVisit(tabId: Long, url: String, title: String?) {
        if (url.isBlank() || url.startsWith("about:") ||
            url.startsWith("data:") || url.startsWith("blob:")
        ) return
        val tab = tabManager.tabs.value.firstOrNull { it.id == tabId } ?: return
        if (tab.lastRecordedUrl == url) return
        tab.lastRecordedUrl = url
        repository.addHistory(url, title.orEmpty())
        repository.updateNavigation(tabId, url, title.orEmpty())
    }

    override fun onCleared() {
        tabManager.tabs.value.forEach { tab ->
            tab.webView?.destroy()
            tab.webView = null
        }
        super.onCleared()
    }

    companion object {
        private const val PREFS_NAME = "ai_browser_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val HISTORY_LIMIT = 300
    }
}

data class Suggestion(
    val title: String,
    val url: String,
    val kind: Kind
) {
    enum class Kind { BOOKMARK, HISTORY }
}

private fun buildSuggestions(
    query: String,
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>
): List<Suggestion> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val seen = mutableSetOf<String>()
    val out = ArrayList<Suggestion>()
    bookmarks.forEach { b ->
        if (b.title.contains(q, true) || b.url.contains(q, true)) {
            if (seen.add(b.url)) {
                out.add(Suggestion(b.title.ifBlank { b.url }, b.url, Suggestion.Kind.BOOKMARK))
            }
        }
    }
    history.forEach { h ->
        if (h.title.contains(q, true) || h.url.contains(q, true)) {
            if (seen.add(h.url)) {
                out.add(Suggestion(h.title.ifBlank { h.url }, h.url, Suggestion.Kind.HISTORY))
            }
        }
    }
    return out.take(6)
}
