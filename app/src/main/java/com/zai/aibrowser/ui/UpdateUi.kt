package com.zai.aibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.update.UpdateState

/**
 * Auto-update dialog: shown when the automatic check discovers a newer release.
 * Reuses the same state machine as the Settings screen.
 */
@Composable
fun UpdateDialog(updateVm: UpdateViewModel) {
    val state by updateVm.state.collectAsStateWithLifecycle()
    val showDialog by updateVm.showDialog.collectAsStateWithLifecycle()
    val relevant = state is UpdateState.Available ||
        state is UpdateState.Downloading ||
        state is UpdateState.Downloaded ||
        state is UpdateState.Error
    if (!showDialog || !relevant) return

    AlertDialog(
        onDismissRequest = updateVm::dismissDialog,
        title = { Text("Update available") },
        text = {
            when (val s = state) {
                is UpdateState.Available -> Column {
                    Text(
                        "Version ${s.release.tagName} is available (installed: v${s.currentVersion})."
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(
                        s.release.body.ifBlank { "See release notes on GitHub." },
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                is UpdateState.Downloading -> Column {
                    Text("Downloading update… ${s.progressPercent}%")
                    LinearProgressIndicator(
                        progress = { s.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                is UpdateState.Downloaded ->
                    Text("Download complete. Tap Install to update the app.")
                is UpdateState.Error -> Text("Update failed: ${s.message}")
                else -> Unit
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Available ->
                    TextButton(onClick = { updateVm.startDownload(s.release) }) { Text("Update") }
                is UpdateState.Downloaded ->
                    TextButton(onClick = { updateVm.install() }) { Text("Install") }
                is UpdateState.Error ->
                    TextButton(onClick = { updateVm.checkForUpdates(true) }) { Text("Retry") }
                else -> TextButton(onClick = updateVm::dismissDialog) { Text("Hide") }
            }
        },
        dismissButton = {
            TextButton(onClick = updateVm::dismissDialog) { Text("Later") }
        }
    )
}
