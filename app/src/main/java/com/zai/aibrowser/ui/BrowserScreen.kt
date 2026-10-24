package com.zai.aibrowser.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.browser.BrowserTab

@Composable
fun AppRoot(browserViewModel: BrowserViewModel, updateViewModel: UpdateViewModel) {
    LaunchedEffect(Unit) { updateViewModel.autoCheckIfDue() }
    Box(modifier = Modifier.fillMaxSize()) {
        BrowserScreen(vm = browserViewModel, updateVm = updateViewModel)
        UpdateDialog(updateViewModel)
    }
}

@Composable
fun BrowserScreen(vm: BrowserViewModel, updateVm: UpdateViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    when (screen) {
        BrowserViewModel.Screen.SETTINGS ->
            SettingsScreen(vm = vm, updateVm = updateVm, onBack = vm::closeSettings)
        BrowserViewModel.Screen.BROWSER -> BrowserChrome(vm)
    }
    Sheets(vm)
}

@Composable
private fun BrowserChrome(vm: BrowserViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val activeTab by vm.activeTab.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val sheet by vm.activeSheet.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.snackbarEvents.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    BackHandler {
        when {
            sheet != BrowserViewModel.Sheet.NONE -> vm.closeSheet()
            activeTab?.canGoBack?.value == true -> vm.goBack()
            else -> (context as? Activity)?.finish()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AddressBar(vm = vm, tab = activeTab)
            val tab: BrowserTab? = activeTab
            if (tab == null) {
                Box(modifier = Modifier.weight(1f))
            } else {
                val epoch by tab.webViewEpoch.collectAsStateWithLifecycle()
                Box(modifier = Modifier.weight(1f)) {
                    key(tab.id, epoch) {
                        if (tab.hasNavigated.collectAsStateWithLifecycle().value) {
                            AndroidView(
                                factory = { ctx -> vm.obtainWebView(tab, ctx) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            StartPage(vm = vm)
                        }
                    }
                    ErrorOverlay(
                        tab = tab,
                        onRetry = vm::retryCurrentTab,
                        onGoBack = vm::goBack
                    )
                }
            }
            BottomBar(vm = vm, tabCount = tabs.size, tab = activeTab)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        )
    }
}
