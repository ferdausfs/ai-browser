package com.zai.aibrowser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * AI task panel (DEVELOPMENT PREVIEW — no real backend).
 *
 * Honesty rules enforced here:
 * - The banner and chips state plainly that the agent backend is not connected.
 * - Progress states (Reading page / Finding element / Taking action / Waiting
 *   for approval) are part of the agent contract; they are shown as PENDING and
 *   never faked as active.
 * - The action history distinguishes SUGGESTED actions from EXECUTED ones; with
 *   the mock provider nothing is ever executed.
 * - Stop cancels the in-flight run for real; Take Control closes the panel.
 * - The disclosure explains what a real backend would send off-device.
 */
@Composable
fun AiPanelContent(vm: BrowserViewModel) {
    val agent by vm.agentState.collectAsStateWithLifecycle()
    val input by vm.aiInput.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text("AI tasks", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Development preview") }
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm::takeControl) { Text("Take control") }
        }
        Spacer(Modifier.height(6.dp))

        // Honest status banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Text(
                "The agent backend is not connected in this build. Tasks are not " +
                    "executed, page content is never collected, and nothing is sent " +
                    "off-device. This panel previews the interaction design only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        // Task input
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = vm::onAiInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Give the AI a task…") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                enabled = !agent.running,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.sendAiTask() })
            )
            Spacer(Modifier.width(8.dp))
            if (agent.running) {
                FilledIconButton(onClick = vm::stopAgentRun) {
                    Icon(Icons.Default.Close, contentDescription = "Stop the AI run")
                }
            } else {
                FilledIconButton(
                    onClick = vm::sendAiTask,
                    enabled = input.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run task")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Progress states — real contract states, never faked as active
        Text(
            "Run states",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            agent.steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (step.state) {
                        BrowserViewModel.AgentStepState.ACTIVE ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        BrowserViewModel.AgentStepState.DONE -> Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        BrowserViewModel.AgentStepState.FAILED -> Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        BrowserViewModel.AgentStepState.PENDING -> Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(8.dp),
                            content = {}
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        step.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step.state == BrowserViewModel.AgentStepState.PENDING)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Action history — SUGGESTED vs EXECUTED, never hidden model reasoning
        Text(
            "Action history",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        if (agent.history.isEmpty()) {
            Text(
                "No actions yet. When a real backend runs, every action appears here " +
                    "with a SUGGESTED or EXECUTED badge. Completion is claimed only " +
                    "after the outcome is verified.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(agent.history) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp)) {
                            val (badgeText, badgeColor) = when (item.kind) {
                                BrowserViewModel.AgentItemKind.SUGGESTED ->
                                    "SUGGESTED" to MaterialTheme.colorScheme.secondaryContainer
                                BrowserViewModel.AgentItemKind.EXECUTED ->
                                    "EXECUTED" to MaterialTheme.colorScheme.primaryContainer
                                BrowserViewModel.AgentItemKind.SYSTEM ->
                                    "STATUS" to MaterialTheme.colorScheme.surface
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor
                            ) {
                                Text(
                                    badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // AI privacy disclosure
        Text(
            "A future real backend will send only the minimum task-relevant page " +
                "excerpt over HTTPS, with your approval before consequential actions. " +
                "Passwords, OTPs, cookies and tokens are never sent. Using a remote " +
                "AI provider is not fully on-device or fully private.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
    }
}
