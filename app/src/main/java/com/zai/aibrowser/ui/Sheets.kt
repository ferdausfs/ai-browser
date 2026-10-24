package com.zai.aibrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheets(vm: BrowserViewModel) {
    val sheet by vm.activeSheet.collectAsStateWithLifecycle()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    when (sheet) {
        BrowserViewModel.Sheet.TABS ->
            ModalBottomSheet(onDismissRequest = vm::closeSheet) { TabsSheetContent(vm) }
        BrowserViewModel.Sheet.BOOKMARKS ->
            ModalBottomSheet(onDismissRequest = vm::closeSheet) { BookmarksSheetContent(vm) }
        BrowserViewModel.Sheet.HISTORY ->
            ModalBottomSheet(onDismissRequest = vm::closeSheet) { HistorySheetContent(vm) }
        BrowserViewModel.Sheet.MENU ->
            ModalBottomSheet(onDismissRequest = vm::closeSheet) {
                MenuSheetContent(
                    onTabs = { vm.openSheet(BrowserViewModel.Sheet.TABS) },
                    onBookmarks = { vm.openSheet(BrowserViewModel.Sheet.BOOKMARKS) },
                    onHistory = { vm.openSheet(BrowserViewModel.Sheet.HISTORY) },
                    onPrivacy = { vm.openSheet(BrowserViewModel.Sheet.PRIVACY) },
                    onClearData = { showClearDialog = true },
                    onSettings = { vm.openSettings() }
                )
            }
        BrowserViewModel.Sheet.AI ->
            ModalBottomSheet(onDismissRequest = vm::closeSheet) { AiPanelContent(vm) }
        BrowserViewModel.Sheet.PRIVACY ->
            ModalBottomSheet(onDismissRequest = vm::closeSheet) { PrivacyDashboardContent(vm) }
        BrowserViewModel.Sheet.NONE -> Unit
    }

    if (showClearDialog) {
        ClearBrowsingDataDialog(
            onConfirm = { tabs, h, c, cache ->
                showClearDialog = false
                vm.closeSheet()
                vm.clearBrowsingData(tabs, h, c, cache)
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun TabsSheetContent(vm: BrowserViewModel) {
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val activeId by vm.activeTabId.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.65f)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Tabs",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tabs, key = { it.id }) { tab ->
                val isActive = tab.id == activeId
                val title by tab.title.collectAsStateWithLifecycle()
                val url by tab.currentUrl.collectAsStateWithLifecycle()
                Surface(
                    color = if (isActive) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { vm.activateTab(tab.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title.ifBlank { "New tab" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { vm.closeTab(tab.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close tab")
                        }
                    }
                }
            }
        }
        Button(
            onClick = vm::newTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("New tab")
        }
    }
}

@Composable
private fun BookmarksSheetContent(vm: BrowserViewModel) {
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.65f)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Bookmarks",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (bookmarks.isEmpty()) {
            Text(
                "No bookmarks yet. Tap the address bar while browsing to open the " +
                    "page tools, then add the page from the menu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(bookmarks, key = { it.id }) { bookmark ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { vm.openSuggestion(bookmark.url) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bookmark.title.ifBlank { bookmark.url },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                bookmark.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { vm.deleteBookmark(bookmark.url) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete bookmark",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySheetContent(vm: BrowserViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.65f)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::clearHistory) { Text("Clear all") }
        }
        if (history.isEmpty()) {
            Text(
                "Pages you visit will appear here. History stays on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(history, key = { it.id }) { entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable { vm.openSuggestion(entry.url) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.title.ifBlank { entry.url },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                entry.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSheetContent(
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onPrivacy: () -> Unit,
    onClearData: () -> Unit,
    onSettings: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        MenuRow(Icons.Default.Tab, "Tabs", onTabs)
        MenuRow(Icons.Default.Star, "Bookmarks", onBookmarks)
        MenuRow(Icons.Default.History, "History", onHistory)
        MenuRow(Icons.Default.Security, "Site privacy", onPrivacy)
        MenuRow(painterResource(R.drawable.ic_erase), "Erase browsing data…", onClearData)
        MenuRow(Icons.Default.Settings, "Settings", onSettings)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
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

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.painter.Painter, label: String, onClick: () -> Unit) {
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

/**
 * Explains what will be cleared BEFORE confirming, including the engine-level
 * scope facts: cookies/site data and cache clearing affect ALL sites; history
 * deletion affects this device's record only; bookmarks are always preserved.
 */
@Composable
fun ClearBrowsingDataDialog(
    onConfirm: (clearTabs: Boolean, clearHistory: Boolean, clearCookies: Boolean, clearCache: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var tabs by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(true) }
    var cookies by rememberSaveable { mutableStateOf(true) }
    var cache by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Erase browsing data") },
        text = {
            Column {
                DialogCheckbox("Open tabs (closes every tab)", tabs) { tabs = it }
                DialogCheckbox("Browsing history (this device only)", history) { history = it }
                DialogCheckbox("Cookies and site data (ALL sites)", cookies) { cookies = it }
                DialogCheckbox("Cached images and files (ALL sites)", cache) { cache = it }
                Text(
                    "Android WebView clears cookies, site data and cache globally — " +
                        "not just for the current site. Bookmarks are always kept. " +
                        "This is not forensic-grade deletion.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tabs, history, cookies, cache) }) { Text("Erase") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DialogCheckbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
