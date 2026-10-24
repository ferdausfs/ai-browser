package com.zai.aibrowser.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Central, auditable WebView hardening + event wiring.
 *
 * Security invariants (enforced here, not in the UI layer):
 * 1. No JavaScript bridge: addJavascriptInterface() is never called, so page content
 *    can never reach app internals. Page content is untrusted data, never instructions.
 * 2. SSL errors are always cancelled — never auto-continued.
 * 3. file://, content://, data:, blob: and javascript: navigations are blocked.
 * 4. Unknown/external schemes do NOT fire arbitrary intents (blocked with a toast).
 *    mailto/tel/sms/geo/market open through the platform resolver only. Phase 4 adds
 *    a strict allowlist with user confirmation for everything else.
 * 5. Safe Browsing stays on; mixed content is never allowed; geolocation is off.
 */
class SecureWebViewFactory(
    private val callbacks: Callbacks
) {

    data class Callbacks(
        val onVisited: (tabId: Long, url: String, title: String?) -> Unit,
        val onPersistTab: (tabId: Long, url: String, title: String) -> Unit,
        val onExternalLinkBlocked: (url: String) -> Unit
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun create(tab: BrowserTab, context: Context): WebView {
        val webView = WebView(context)
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.allowFileAccessFromFileURLs = false
        s.allowUniversalAccessFromFileURLs = false
        s.mediaPlaybackRequiresUserGesture = true
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(false)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.safeBrowsingEnabled = true
        s.setGeolocationEnabled(false)
        s.userAgentString = s.userAgentString + " AiBrowser/1.0"
        webView.isFocusableInTouchMode = true

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleUrl(request.url.toString())
            }

            private fun handleUrl(url: String): Boolean {
                val scheme = Uri.parse(url).scheme?.lowercase()
                return when (scheme) {
                    "http", "https", "about" -> false
                    "mailto", "tel", "sms", "geo", "market" -> { launchExternal(context, url); true }
                    else -> { callbacks.onExternalLinkBlocked(url); true }
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                tab.error.value = null
                tab.isLoading.value = true
                tab.currentUrl.value = url
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                tab.currentUrl.value = url
                tab.canGoBack.value = view.canGoBack()
                tab.canGoForward.value = view.canGoForward()
                callbacks.onVisited(tab.id, url, view.title)
            }

            override fun onPageFinished(view: WebView, url: String) {
                tab.isLoading.value = false
                tab.progress.value = 100
                tab.currentUrl.value = url
                tab.title.value = view.title ?: tab.title.value
                tab.canGoBack.value = view.canGoBack()
                tab.canGoForward.value = view.canGoForward()
                callbacks.onPersistTab(tab.id, url, view.title ?: "")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // SECURITY: never proceed past an invalid certificate.
                handler.cancel()
                tab.isLoading.value = false
                tab.error.value = PageError(
                    isSslError = true,
                    description = "SSL certificate error (code ${error.primaryError}). " +
                        "AI Browser never bypasses certificate errors.",
                    url = error.url
                )
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    tab.isLoading.value = false
                    tab.error.value = PageError(
                        isSslError = false,
                        description = error.description?.toString() ?: "Failed to load page",
                        url = request.url.toString()
                    )
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Recover instead of taking the whole app down (API 26+).
                if (tab.webView === view) {
                    tab.webView = null
                    tab.isLoading.value = false
                    tab.error.value = PageError(
                        isSslError = false,
                        description = "The page renderer crashed. Press Try again to reload.",
                        url = tab.currentUrl.value
                    )
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress.value = newProgress
                tab.isLoading.value = newProgress < 100
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title.value = title ?: ""
            }
        }
        return webView
    }

    private fun launchExternal(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            callbacks.onExternalLinkBlocked(url)
        }
    }
}
