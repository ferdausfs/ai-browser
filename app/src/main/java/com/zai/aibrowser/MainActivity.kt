package com.zai.aibrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.ui.AppRoot
import com.zai.aibrowser.ui.BrowserViewModel
import com.zai.aibrowser.ui.UpdateViewModel
import com.zai.aibrowser.ui.theme.AiBrowserTheme

class MainActivity : ComponentActivity() {

    private val browserViewModel: BrowserViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by browserViewModel.themeMode.collectAsStateWithLifecycle()
            AiBrowserTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .imePadding()
                    ) {
                        AppRoot(browserViewModel, updateViewModel)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        browserViewModel.onHostPause()
    }

    override fun onResume() {
        super.onResume()
        browserViewModel.onHostResume()
    }
}
