package com.zai.aibrowser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.aibrowser.privacy.TrackerBlocker
import java.net.URLEncoder

/**
 * Site privacy dashboard — shows ONLY facts the app can actually verify:
 * domain, transport encryption, real blocked-request count for the current page,
 * the per-site tracker exception, engine-level permission policy and site-data
 * clearing with its true (global) scope.
 *
 * Honest by design: no invented privacy scores, no fake block totals, and a
 * standing note that HTTPS encryption is not a trustworthiness guarantee.
 */
@Composable
fun PrivacyDashboardContent(vm: BrowserViewModel) {
    val tab by vm.activeTab.collectAsStateWithLifecycle()
    val url = tab?.currentUrl?.collectAsStateWithLifecycle()?.value.orEmpty()
    val blockedOnPage = tab?.blockedCount?.collectAsStateWithLifecycle()?.value ?: 0
    val stats by vm.privacyStats.collectAsStateWithLifecycle()
    val exceptions by vm.siteExceptions.collectAsStateWithLifecycle()

    val host = remember(url) { hostOf(url) }
    val site = remember(host) { TrackerBlocker.registrableDomain(host) }
    val isHttp = url.startsWith("http://")
    val isHttps = url.startsWith("https://")
    val isException = site != null && site in exceptions

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Header: the current site
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isHttps) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (isHttps) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    host ?: "No website open",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (host == null) "Open a website to see its privacy details"
                    else url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Connection (verified from the URL scheme only)
        SectionTitle("Connection")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isHttps) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    if (isHttps) "Encrypted (HTTPS)"
                    else if (isHttp) "Not encrypted (HTTP)"
                    else "Unknown",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Text(
            "Encryption protects data in transit. It does NOT mean the site is trustworthy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(14.dp))

        // Tracking protection (real counter + per-site exception)
        SectionTitle("Tracking protection")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Blocked on this page: $blockedOnPage request" +
                        if (blockedOnPage == 1) "" else "s",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Today: ${stats.blockedToday} · All time: ${stats.blockedTotal} " +
                        "(requests actually blocked; not a total of all tracking)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (site != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow trackers on $site", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (isException) "Trackers are currently allowed on this site"
                        else "Use only if this site breaks; report it so the list can improve",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isException,
                    onCheckedChange = { vm.toggleTrackerException(site) }
                )
            }
            AssistChip(
                onClick = {
                    val title = URLEncoder.encode(
                        "Site broken with tracking protection: $site", "UTF-8"
                    )
                    vm.openBrowserAndNavigate(
                        "https://github.com/ferdausfs/ai-browser/issues/new?title=$title"
                    )
                },
                label = { Text("Report broken site") }
            )
        }
        Spacer(Modifier.height(14.dp))

        // Site permissions (engine-level policy — what the app truly enforces)
        SectionTitle("Site permissions")
        PermissionRow(Icons.Default.LocationOff, "Location", "Always blocked in this browser")
        PermissionRow(Icons.Default.VideocamOff, "Camera", "Always blocked in this browser")
        PermissionRow(Icons.Default.MicOff, "Microphone", "Always blocked in this browser")
        Text(
            "Requests are denied at the engine level; Aegis holds no Android " +
                "camera, microphone or location permissions.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(14.dp))

        // Site data clearing — accurate scope statement
        SectionTitle("Site data")
        TextButton(onClick = {
            vm.clearBrowsingData(clearTabs = false, clearHistory = false, clearCookies = true, clearCache = false)
            vm.closeSheet()
        }) {
            Text("Clear cookies & site data (all sites)")
        }
        Text(
            "Android WebView clears cookies and site data globally, not per site. " +
                "Bookmarks and history are kept.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "What this dashboard cannot tell you",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Android WebView cannot see every tracking channel (DNS lookups, " +
                        "service workers, WebRTC), so block counts are a floor. There is " +
                        "no privacy score here on purpose: it would be invented.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun PermissionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
