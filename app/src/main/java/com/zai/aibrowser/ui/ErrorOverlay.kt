package com.zai.aibrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.browser.BrowserTab

/**
 * Full-page error state with accurate messaging: SSL errors never offer a
 * bypass; offline is detected and stated; renderer crashes offer recovery.
 */
@Composable
fun ErrorOverlay(
    tab: BrowserTab,
    online: Boolean,
    onRetry: () -> Unit,
    onGoBack: () -> Unit,
    onGoHome: () -> Unit
) {
    val error by tab.error.collectAsStateWithLifecycle()
    val current = error ?: return
    val offline = !online
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when {
                        offline -> Icons.Default.CloudOff
                        current.isSslError -> Icons.Default.Lock
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = if (offline) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        offline -> "You're offline"
                        current.isSslError -> "Connection not secure"
                        else -> "Can't open this page"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        offline -> "Check your connection, then try again."
                        current.isSslError -> current.description
                        else -> current.description
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    current.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (current.isSslError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Aegis Browser never bypasses certificate errors.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onGoHome) { Text("Home") }
                    TextButton(onClick = onGoBack) { Text("Go back") }
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}
