package com.zai.aibrowser.browser

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One runtime tab. The WebView is created lazily when the tab is first shown. */
class BrowserTab(
    val id: Long,
    initialUrl: String
) {
    val currentUrl = MutableStateFlow(initialUrl)
    val title = MutableStateFlow("")
    val progress = MutableStateFlow(0)
    val isLoading = MutableStateFlow(false)
    val canGoBack = MutableStateFlow(false)
    val canGoForward = MutableStateFlow(false)
    val error = MutableStateFlow<PageError?>(null)
    val hasNavigated = MutableStateFlow(initialUrl.isNotBlank() && initialUrl != "about:blank")

    /** Bumped when a dead WebView must be recreated, forcing a fresh AndroidView node. */
    val webViewEpoch = MutableStateFlow(0)

    var lastRecordedUrl: String? = null
    var webView: WebView? = null
}

data class PageError(
    val isSslError: Boolean,
    val description: String,
    val url: String
)

/** In-memory tab registry; persistence lives in the Room-backed repository. */
class TabManager {

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Long?>(null)
    val activeTabId: StateFlow<Long?> = _activeTabId.asStateFlow()

    val activeTab: BrowserTab?
        get() = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    fun setTabs(list: List<BrowserTab>, activeId: Long?) {
        _tabs.value = list
        _activeTabId.value = activeId ?: list.firstOrNull()?.id
    }

    fun addTab(tab: BrowserTab) {
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
    }

    fun removeTab(id: Long): BrowserTab? {
        val removed = _tabs.value.firstOrNull { it.id == id } ?: return null
        val remaining = _tabs.value - removed
        _tabs.value = remaining
        if (_activeTabId.value == id) {
            _activeTabId.value = remaining.lastOrNull()?.id
        }
        return removed
    }

    fun activate(id: Long) {
        _activeTabId.value = id
    }
}
