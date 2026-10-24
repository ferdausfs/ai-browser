package com.zai.aibrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.browser.BrowserTab
import java.net.URI

/**
 * One-handed browser chrome: a compact bottom control area
 * (back · forward · address/search · tabs · menu) with a loading progress line
 * on its top edge. The AI entry is a floating chip that never crowds navigation;
 * tapping the lock/shield in the address pill opens the site privacy dashboard.
 */

@Composable
fun BottomControls(vm: BrowserViewModel, tab: BrowserTab?, tabCount: Int) {
    val canGoBack = tab?.canGoBack?.collectAsStateWithLifecycle()?.value ?: false
    val canGoForward = tab?.canGoForward?.collectAsStateWithLifecycle()?.value ?: false
    val loading = tab?.isLoading?.collectAsStateWithLifecycle()?.value ?: false
    val progress = tab?.progress?.collectAsStateWithLifecycle()?.value ?: 100
    val url = tab?.currentUrl?.collectAsStateWithLifecycle()?.value.orEmpty()
    val host = remember(url) { hostOf(url) }
    val onHomePage = tab?.hasNavigated?.collectAsStateWithLifecycle()?.value != true

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            if (loading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::goBack, enabled = canGoBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back"
                    )
                }
                IconButton(onClick = vm::goForward, enabled = canGoForward) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go forward"
                    )
                }
                AddressPill(
                    vm = vm,
                    host = host,
                    isSecure = url.startsWith("https://"),
                    onHomePage = onHomePage,
                    loading = loading,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                IconButton(onClick = { vm.openSheet(BrowserViewModel.Sheet.TABS) }) {
                    BadgedBox(badge = { Badge { Text(text = tabCount.toString()) } }) {
                        Icon(Icons.Default.Tab, contentDescription = "Tabs ($tabCount open)")
                    }
                }
                IconButton(onClick = { vm.openSheet(BrowserViewModel.Sheet.MENU) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
            }
        }
    }
}

/** Compact address field showing the readable domain; lock tap = site privacy. */
@Composable
private fun AddressPill(
    vm: BrowserViewModel,
    host: String?,
    isSecure: Boolean,
    onHomePage: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    val editDescription = "Address and search bar"
    val privacyDescription = if (host != null) {
        "Site privacy for $host"
    } else {
        "Site privacy"
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .height(44.dp)
            .semantics { contentDescription = editDescription }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        if (onHomePage) vm.onAddressEditing(true)
                        else vm.openSheet(BrowserViewModel.Sheet.PRIVACY)
                    }
                    .semantics { contentDescription = privacyDescription },
                contentAlignment = Alignment.Center
            ) {
                when {
                    onHomePage -> Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    isSecure -> Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    else -> Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Text(
                text = when {
                    onHomePage -> "Search or type URL"
                    host != null -> host
                    else -> "Loading…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (onHomePage) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { vm.onAddressEditing(true) }
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

/** Full-screen editor shown while typing a URL / search / AI-free navigation. */
@Composable
fun AddressEditorOverlay(vm: BrowserViewModel) {
    val editing by vm.isEditingAddress.collectAsStateWithLifecycle()
    val address by vm.addressInput.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    if (!editing) return
    BackHandler { vm.onAddressEditing(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { vm.onAddressEditing(false) }
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { /* consume clicks inside the sheet */ }
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.onAddressEditing(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Close address bar")
                    }
                    TextField(
                        value = address,
                        onValueChange = vm::onAddressChanged,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text("Search or type URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Go,
                            keyboardType = KeyboardType.Uri
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            vm.commitAddress(address)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
                LazyColumn(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
                ) {
                    items(suggestions, key = { it.url }) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.openSuggestion(s.url) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (s.kind == Suggestion.Kind.BOOKMARK) Icons.Default.Star
                                else Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    s.url,
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
}

/** Floating AI entry point — easy to reach, never crowding the nav row. */
@Composable
fun AiFloatingChip(vm: BrowserViewModel, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = { vm.openSheet(BrowserViewModel.Sheet.AI) },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.semantics {
            contentDescription = "AI tasks (development preview)"
        }
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null)
    }
}

internal fun hostOf(url: String): String? = try {
    if (url.startsWith("about:") || url.isBlank()) null else URI(url).host
} catch (_: Exception) {
    null
}
