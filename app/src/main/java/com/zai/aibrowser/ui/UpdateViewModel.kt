package com.zai.aibrowser.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zai.aibrowser.BuildConfig
import com.zai.aibrowser.update.GithubRelease
import com.zai.aibrowser.update.UpdateManager
import com.zai.aibrowser.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the whole self-update state machine:
 * check -> (up to date | available) -> download -> install hand-off.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = UpdateManager(app)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _autoCheckEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK, true))
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheckEnabled.asStateFlow()

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        _autoCheckEnabled.value = enabled
    }

    /**
     * @param isAutoCheck true when triggered automatically on app start (shows a
     * dialog when an update is found); false when triggered from Settings.
     */
    fun checkForUpdates(isAutoCheck: Boolean) {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val release = manager.fetchLatestRelease()
                val currentVersion = BuildConfig.VERSION_NAME
                if (manager.isNewerVersion(release.tagName, currentVersion)) {
                    if (isAutoCheck) _showDialog.value = true
                    _state.value = UpdateState.Available(release, currentVersion)
                } else {
                    _state.value = UpdateState.UpToDate(currentVersion)
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Could not check for updates")
            }
        }
    }

    /** Runs at most once per 24h when auto-check is enabled. */
    fun autoCheckIfDue() {
        if (!_autoCheckEnabled.value) return
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (now - last < AUTO_CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        checkForUpdates(isAutoCheck = true)
    }

    fun startDownload(release: GithubRelease) {
        _state.value = UpdateState.Downloading(release, 0)
        viewModelScope.launch {
            try {
                manager.downloadApk(release).collect { percent ->
                    _state.value = UpdateState.Downloading(release, percent)
                }
                val file = manager.downloadedApkFile(release)
                _state.value = UpdateState.Downloaded(release, file.absolutePath)
            } catch (e: Exception) {
                _state.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun install() {
        val downloaded = _state.value as? UpdateState.Downloaded ?: return
        if (!manager.canInstallPackages()) {
            manager.openInstallPermissionSettings()
            return
        }
        try {
            manager.installApk(java.io.File(downloaded.filePath))
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Install failed: ${e.message}")
        }
    }

    fun dismissDialog() {
        _showDialog.value = false
    }

    companion object {
        private const val PREFS_NAME = "ai_browser_prefs"
        private const val KEY_AUTO_CHECK = "auto_update_check"
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
