package com.zai.aibrowser

import android.app.Application
import android.webkit.WebView

/**
 * Application entry point. Deliberately minimal: dependencies are created lazily
 * where they are needed (no service locator, no reflection) so that each agent
 * layer stays easy to test and to swap in later phases.
 */
class AiBrowserApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // SECURITY: WebView remote debugging is pinned OFF in release builds
        // (it stays available for developers in debug builds only).
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }
}
