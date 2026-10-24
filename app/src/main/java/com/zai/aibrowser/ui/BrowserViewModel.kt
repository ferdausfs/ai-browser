package com.zai.aibrowser.ui

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
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
import com.zai.aibrowser.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
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
 * Owns all browser state: tabs, address bar, bookmarks, history, sheets, theme and
 * the Phase 1 mock AI command bar. Pure state orchestration — every WebView touch
 * goes through [BrowserTab], every DB touch goes through [BrowserRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = BrowserRepository(AppDatabase.get(app))
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
    enum class Sheet { NONE, TABS, BOOKMARKS, HISTORY, MENU, AI }

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

    // ---------- AI command bar (Phase 1: mock wiring) ----------

    val aiMessages = MutableStateFlow(
        listOf(
            AiMessage(
                role = AiMessage.Role.AGENT,
                text = "AI command center is wired and running. From Phase 2 I will read the " +
                    "current page, plan actions and execute them step by step. For now the " +
                    "pipeline is confirmed with the mock provider."
            )
        )
    )
    val aiInput = MutableStateFlow("")
    private val agentController = MockAgentController(MockAiProvider())

    fun onAiInputChanged(text: String) {
        aiInput.value = text
    }

    fun sendAiCommand() {
        val command = aiInput.value.trim()
        if (command.isEmpty()) return
        aiInput.value = ""
        aiMessages.value = aiMessages.value + AiMessage(AiMessage.Role.USER, command)
        viewModelScope.launch {
            val reply = agentController.respondToCommand(command)
            aiMessages.value = aiMessages.value + AiMessage(AiMessage.Role.AGENT, reply)
        }
    }

    // ---------- WebView factory wiring ----------

    private val webViewFactory = SecureWebViewFactory(
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

    fun obtainWebView(tab: BrowserTab, context: Context): WebView {
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
    }

    fun onHostResume() {
        activeTab.value?.webView?.onResume()
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
        val url = UrlNormalizer.normalize(raw) ?: return
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

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            tabManager.tabs.value.forEach { it.lastRecordedUrl = null }
            notify("History cleared")
        }
    }

    fun clearBrowsingData(clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean) {
        viewModelScope.launch {
            if (clearHistory) {
                repository.clearHistory()
                tabManager.tabs.value.forEach { it.lastRecordedUrl = null }
            }
            if (clearCookies) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            if (clearCache) {
                tabManager.tabs.value.forEach { it.webView?.clearCache(true) }
            }
            notify("Browsing data cleared")
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

data class AiMessage(
    val role: Role,
    val text: String
) {
    enum class Role { USER, AGENT }
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
