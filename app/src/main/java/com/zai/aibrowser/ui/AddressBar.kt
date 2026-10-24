package com.zai.aibrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.browser.BrowserTab

@Composable
fun AddressBar(vm: BrowserViewModel, tab: BrowserTab?) {
    val focusManager = LocalFocusManager.current
    val address by vm.addressInput.collectAsStateWithLifecycle()
    val editing by vm.isEditingAddress.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val bookmarked by vm.isBookmarked.collectAsStateWithLifecycle()
    val url = tab?.currentUrl?.collectAsStateWithLifecycle()?.value.orEmpty()
    val loading = tab?.isLoading?.collectAsStateWithLifecycle()?.value ?: false
    val progress = tab?.progress?.collectAsStateWithLifecycle()?.value ?: 100
    val insecure = url.startsWith("http://")

    Surface(color = MaterialTheme.colorScheme.surface) {
        Box {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = address,
                        onValueChange = vm::onAddressChanged,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { vm.onAddressEditing(it.isFocused) },
                        placeholder = { Text("Search or type URL") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = if (insecure) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (insecure) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { if (loading) vm.stop() else vm.reload() }) {
                                Icon(
                                    if (loading) Icons.Default.Close else Icons.Default.Refresh,
                                    contentDescription = if (loading) "Stop" else "Reload"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Go,
                            keyboardType = KeyboardType.Uri
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            vm.commitAddress(address)
                            focusManager.clearFocus()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(onClick = { vm.toggleBookmark() }) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Bookmark this page",
                            tint = if (bookmarked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { vm.openSheet(BrowserViewModel.Sheet.MENU) }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                }
                if (loading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = editing && suggestions.isNotEmpty(),
                onDismissRequest = { vm.onAddressEditing(false) },
                properties = PopupProperties(focusable = false)
            ) {
                suggestions.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(s.title, maxLines = 1)
                                Text(
                                    s.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (s.kind == Suggestion.Kind.BOOKMARK) {
                                    Icons.Default.Star
                                } else {
                                    Icons.Default.History
                                },
                                contentDescription = null
                            )
                        },
                        onClick = {
                            vm.openSuggestion(s.url)
                            focusManager.clearFocus()
                        }
                    )
                }
            }
        }
    }
}
