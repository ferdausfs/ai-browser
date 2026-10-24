package com.zai.aibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.BuildConfig
import com.zai.aibrowser.privacy.SearchEngines
import com.zai.aibrowser.update.UpdateConfig
import com.zai.aibrowser.update.UpdateState
import com.zai.aibrowser.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: BrowserViewModel, updateVm: UpdateViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        SettingsSection("Appearance") {
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                FilterChip(
                    selected = mode == ThemeMode.SYSTEM,
                    onClick = { vm.setThemeMode(ThemeMode.SYSTEM) },
                    label = { Text("System") }
                )
                FilterChip(
                    selected = mode == ThemeMode.LIGHT,
                    onClick = { vm.setThemeMode(ThemeMode.LIGHT) },
                    label = { Text("Light") }
                )
                FilterChip(
                    selected = mode == ThemeMode.DARK,
                    onClick = { vm.setThemeMode(ThemeMode.DARK) },
                    label = { Text("Dark") }
                )
            }
        }

        SettingsSection("Search engine") {
            val engineId by vm.searchEngineId.collectAsStateWithLifecycle()
            Text(
                "Used when you type a query instead of a URL.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            ) {
                SearchEngines.ALL.take(3).forEach { engine ->
                    FilterChip(
                        selected = engineId == engine.id,
                        onClick = { vm.setSearchEngine(engine.id) },
                        label = { Text(engine.label) }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SearchEngines.ALL.drop(3).forEach { engine ->
                    FilterChip(
                        selected = engineId == engine.id,
                        onClick = { vm.setSearchEngine(engine.id) },
                        label = { Text(engine.label) }
                    )
                }
            }
        }

        SettingsSection("Privacy") {
            val trackingOn by vm.trackingProtection.collectAsStateWithLifecycle()
            val thirdPartyCookies by vm.thirdPartyCookies.collectAsStateWithLifecycle()
            val safeBrowsingOn by vm.safeBrowsing.collectAsStateWithLifecycle()
            PrivacySwitchRow(
                title = "Tracking protection",
                caption = "Blocks known tracker/ad/malware domains on third-party " +
                    "requests using an embedded blocklist. WebView cannot see every " +
                    "channel (DNS, service workers, WebRTC) — this is not complete " +
                    "anti-tracking.",
                checked = trackingOn,
                onChecked = vm::setTrackingProtection
            )
            PrivacySwitchRow(
                title = "Allow third-party cookies",
                caption = "Blocked by default. Some sign-ins may break; enabling this " +
                    "lets sites track you across other sites.",
                checked = thirdPartyCookies,
                onChecked = vm::setThirdPartyCookies
            )
            PrivacySwitchRow(
                title = "Safe Browsing",
                caption = "Warns about dangerous sites. URL checks are handled by the " +
                    "Android System WebView (Google service).",
                checked = safeBrowsingOn,
                onChecked = vm::setSafeBrowsing
            )
            var showClearDialog by rememberSaveable { mutableStateOf(false) }
            SettingRow(icon = Icons.Default.Delete, label = "Erase browsing data…") {
                showClearDialog = true
            }
            if (showClearDialog) {
                ClearBrowsingDataDialog(
                    onConfirm = { tabs, h, c, cache ->
                        showClearDialog = false
                        vm.clearBrowsingData(tabs, h, c, cache)
                    },
                    onDismiss = { showClearDialog = false }
                )
            }
        }

        SettingsSection("Updates") {
            UpdateSection(updateVm)
        }

        SettingsSection("About") {
            SettingRow(icon = Icons.Default.Info, label = "Source & releases on GitHub") {
                vm.openBrowserAndNavigate(UpdateConfig.RELEASES_PAGE_URL)
            }
            Text(
                "Aegis Browser v" + BuildConfig.VERSION_NAME +
                    " · Engine: Android System WebView — keep it updated for security fixes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                "Browsing data stays on this device. No analytics or telemetry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    caption: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun UpdateSection(updateVm: UpdateViewModel) {
    val state by updateVm.state.collectAsStateWithLifecycle()
    val autoCheck by updateVm.autoCheckEnabled.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Current version: v" + BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.padding(top = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Check for updates automatically",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = autoCheck, onCheckedChange = { updateVm.setAutoCheckEnabled(it) })
        }
        Spacer(Modifier.padding(top = 8.dp))
        when (val s = state) {
            is UpdateState.Idle ->
                Button(onClick = { updateVm.checkForUpdates(false) }) { Text("Check for updates") }
            is UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking…", style = MaterialTheme.typography.bodyMedium)
            }
            is UpdateState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("You're up to date", style = MaterialTheme.typography.bodyMedium)
            }
            is UpdateState.Available -> Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Update available: ${s.release.tagName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (s.release.body.isNotBlank()) {
                        Spacer(Modifier.padding(top = 4.dp))
                        Text(
                            s.release.body,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.padding(top = 8.dp))
                    Button(onClick = { updateVm.startDownload(s.release) }) {
                        Text("Download and install")
                    }
                }
            }
            is UpdateState.Downloading -> Column {
                Text("Downloading… ${s.progressPercent}%", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { s.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
            is UpdateState.Downloaded -> Column {
                Text("Update downloaded. Ready to install.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(top = 8.dp))
                Button(onClick = { updateVm.install() }) { Text("Install update") }
                Text(
                    "If installation does not start, allow \"Install unknown apps\" for Aegis Browser.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            is UpdateState.Error -> Column {
                Text(
                    "Update check failed: ${s.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { updateVm.checkForUpdates(false) }) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
