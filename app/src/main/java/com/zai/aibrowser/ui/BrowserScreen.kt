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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val editing by vm.isEditingAddress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Checked when the chrome is composed; Retry re-composes the error overlay.
    val online = remember { vm.isOnline() }

    LaunchedEffect(Unit) {
        vm.snackbarEvents.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    BackHandler {
        when {
            sheet != BrowserViewModel.Sheet.NONE -> vm.closeSheet()
            editing -> vm.onAddressEditing(false)
            activeTab?.canGoBack?.value == true -> vm.goBack()
            else -> (context as? Activity)?.finish()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val tab: BrowserTab? = activeTab
            Box(modifier = Modifier.weight(1f)) {
                if (tab == null) {
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    val epoch by tab.webViewEpoch.collectAsStateWithLifecycle()
                    val hasNavigated by tab.hasNavigated.collectAsStateWithLifecycle()
                    key(tab.id, epoch) {
                        if (hasNavigated) {
                            AndroidView(
                                factory = { ctx -> vm.obtainWebView(tab, ctx) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            HomeScreen(vm = vm)
                        }
                    }
                    ErrorOverlay(
                        tab = tab,
                        online = online,
                        onRetry = vm::retryCurrentTab,
                        onGoBack = vm::goBack,
                        onGoHome = vm::goHome
                    )
                }
            }
            BottomControls(vm = vm, tab = activeTab, tabCount = tabs.size)
        }

        // AI entry: floating above the bottom bar, out of the way of navigation.
        if (!editing) {
            AiFloatingChip(
                vm = vm,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 84.dp)
            )
        }

        AddressEditorOverlay(vm)

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp)
        )
    }
}
