package app.ownplay.player.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ownplay.player.live.LiveChannelItem

@Composable
fun ChannelCustomizationDialog(
    channel: LiveChannelItem,
    onSetLocalDisplayName: (channelId: String, name: String) -> Unit,
    onClearLocalDisplayName: (channelId: String) -> Unit,
    onSetLogoOverride: (channelId: String, logoValue: String) -> Unit,
    onClearLogoOverride: (channelId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var localName by remember(channel.channelId) {
        mutableStateOf(channel.localDisplayName.orEmpty())
    }
    var logoValue by remember(channel.channelId) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize channel") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = channel.providerName,
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = localName,
                    onValueChange = { localName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Local channel name") },
                    supportingText = { Text("Provider data is not modified.") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (channel.localDisplayName != null) {
                        TextButton(
                            onClick = {
                                onClearLocalDisplayName(channel.channelId)
                                localName = ""
                            },
                        ) {
                            Text("Use provider name")
                        }
                    }
                    TextButton(
                        onClick = {
                            val normalized = localName.trim()
                            if (normalized.isNotEmpty()) {
                                onSetLocalDisplayName(channel.channelId, normalized)
                            }
                        },
                        enabled = localName.isNotBlank(),
                    ) {
                        Text("Save name")
                    }
                }

                HorizontalDivider()

                Text(
                    text = if (channel.hasLogoOverride) {
                        "A local logo override is active."
                    } else {
                        "No local logo override."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = logoValue,
                    onValueChange = { logoValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("New logo URL or URI") },
                    supportingText = {
                        Text("Sensitive locator values are stored outside the Room database.")
                    },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (channel.hasLogoOverride) {
                        TextButton(
                            onClick = {
                                onClearLogoOverride(channel.channelId)
                                logoValue = ""
                            },
                        ) {
                            Text("Clear logo")
                        }
                    }
                    TextButton(
                        onClick = {
                            val normalized = logoValue.trim()
                            if (normalized.isNotEmpty()) {
                                onSetLogoOverride(channel.channelId, normalized)
                                logoValue = ""
                            }
                        },
                        enabled = logoValue.isNotBlank(),
                    ) {
                        Text("Set logo")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
