package com.adochi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.adochi.core.LinkInspector
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.IconBadge
import com.adochi.ui.theme.warning

@Suppress("DEPRECATION") // LocalClipboardManager: the suspend Clipboard API is overkill for a paste button.
@Composable
fun LinkCheckDialog(initial: String, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(initial) }
    var report by remember { mutableStateOf(if (initial.isNotBlank()) LinkInspector.inspect(initial) else null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check a link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Paste a link you received. AdOchi checks it on your phone against scam and phishing lists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        report = null
                    },
                    placeholder = { Text("https://…") },
                    maxLines = 3,
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let {
                                text = it
                                report = LinkInspector.inspect(it)
                            }
                        }) { Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                report?.let { ReportCard(it) }
            }
        },
        confirmButton = {
            val current = report
            if (current?.url != null && current.level != LinkInspector.Level.INVALID) {
                TextButton(onClick = { onOpen(current.url) }) {
                    Text(if (current.level == LinkInspector.Level.SAFE) "Open in Clean Browser" else "Open anyway")
                }
            } else {
                Button(onClick = { report = LinkInspector.inspect(text) }, enabled = text.isNotBlank()) { Text("Check") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ReportCard(report: LinkInspector.Report) {
    val colors = MaterialTheme.colorScheme
    val (icon, tint, title) = when (report.level) {
        LinkInspector.Level.DANGER -> Triple(Icons.Rounded.GppBad, colors.error, "Dangerous: do not open")
        LinkInspector.Level.WARNING -> Triple(Icons.Rounded.GppMaybe, colors.warning, "Be careful")
        LinkInspector.Level.SAFE -> Triple(Icons.Rounded.GppGood, colors.primary, "No known problems")
        LinkInspector.Level.INVALID -> Triple(Icons.AutoMirrored.Rounded.HelpOutline,colors.onSurfaceVariant, "Can't check this")
    }
    AppCard(border = tint.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                report.host?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(8.dp))
        report.reasons.forEach { reason ->
            Text("• $reason", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
        }
        if (report.level == LinkInspector.Level.SAFE) {
            Text(
                "Not on any list, but stay alert: never share your PIN, OTP or card number through a link.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
