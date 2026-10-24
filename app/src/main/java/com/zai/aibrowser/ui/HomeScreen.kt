package com.zai.aibrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.R

private data class Shortcut(val label: String, val url: String, val icon: ImageVector? = null)

private val defaultShortcuts = listOf(
    Shortcut("Wikipedia", "https://www.wikipedia.org"),
    Shortcut("DuckDuckGo", "https://duckduckgo.com"),
    Shortcut("GitHub", "https://github.com"),
    Shortcut("BBC News", "https://www.bbc.com/news"),
    Shortcut("Hacker News", "https://news.ycombinator.com")
)

/**
 * Home screen: uncluttered, one prominent field for URL / search / AI task with
 * explicit mode selection, useful shortcuts, a real privacy counter and a quick
 * erase action. No ad feed, no decorative clutter.
 */
@Composable
fun HomeScreen(vm: BrowserViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    // MODE_BROWSE: navigate/search; MODE_AI: hand the text to the AI task panel.
    var aiMode by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val stats by vm.privacyStats.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()

    fun submit() {
        focusManager.clearFocus()
        if (aiMode) {
            if (query.isNotBlank()) {
                vm.onAiInputChanged(query)
                query = ""
                vm.openSheet(BrowserViewModel.Sheet.AI)
            }
        } else {
            vm.commitAddress(query)
            query = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Identity
        Icon(
            painter = painterResource(R.drawable.ic_shield_logo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Aegis",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Private web, with an AI at your side",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        // Mode selection: Browse/Search vs AI task
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !aiMode,
                onClick = { aiMode = false },
                label = { Text("Browse / search") }
            )
            FilterChip(
                selected = aiMode,
                onClick = { aiMode = true },
                label = { Text("AI task") }
            )
        }
        Spacer(Modifier.height(12.dp))

        // One prominent unified field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    if (aiMode) "Give the AI a task…" else "Search or type a URL"
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            leadingIcon = {
                Icon(
                    if (aiMode) Icons.Default.AutoAwesome else Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                Button(onClick = { submit() }) {
                    Text(if (aiMode) "Run" else "Go")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() })
        )
        if (aiMode) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Development preview: the agent backend is not connected. " +
                    "No page data is collected or sent anywhere.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))

        // Shortcuts: real bookmarks first, sensible defaults when empty
        val shortcuts = if (bookmarks.isEmpty()) {
            defaultShortcuts
        } else {
            bookmarks.take(6).map { Shortcut(it.title.ifBlank { it.url }, it.url) }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(shortcuts) { s ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { vm.openSuggestion(s.url) }
                ) {
                    Text(
                        s.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        // Real privacy status + quick erase
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Trackers blocked",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = buildString {
                            append("Today: ")
                            append(formatCount(stats.blockedToday))
                            append("   ·   All time: ")
                            append(formatCount(stats.blockedTotal))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Counts requests actually blocked. Not a total of all tracking.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_erase),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Erase",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.clickable { vm.openSheet(BrowserViewModel.Sheet.MENU) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your history, bookmarks and settings stay on this device. " +
                "No ads, no analytics, no telemetry.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatCount(n: Long): String = java.text.NumberFormat.getIntegerInstance().format(n)
