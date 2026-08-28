package app.ownplay.player.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.live.LiveCustomGroup

@Composable
fun CustomGroupManagerDialog(
    groups: List<LiveCustomGroup>,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newGroupName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LiveCustomGroup?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LiveCustomGroup?>(null) }

    val groupToRename = renameTarget
    val groupToDelete = deleteTarget

    when {
        groupToRename != null -> {
            AlertDialog(
                onDismissRequest = {
                    renameTarget = null
                    renameValue = ""
                },
                title = { Text("Rename group") },
                text = {
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Group name") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val normalized = renameValue.trim()
                            if (normalized.isNotEmpty()) {
                                onRenameGroup(groupToRename.groupId, normalized)
                                renameTarget = null
                                renameValue = ""
                            }
                        },
                        enabled = renameValue.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            renameTarget = null
                            renameValue = ""
                        },
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        groupToDelete != null -> {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete ${groupToDelete.name}?") },
                text = {
                    Text(
                        "The group and its local memberships will be removed. " +
                            "Channels and provider data stay unchanged.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteGroup(groupToDelete.groupId)
                            deleteTarget = null
                        },
                    ) {
                        Text(
                            text = "Delete group",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        else -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Channel groups") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Create local groups to organize channels without changing provider data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Group name") },
                            singleLine = true,
                        )
                        TextButton(
                            onClick = {
                                val normalized = newGroupName.trim()
                                if (normalized.isNotEmpty()) {
                                    onCreateGroup(normalized)
                                    newGroupName = ""
                                }
                            },
                            enabled = newGroupName.isNotBlank(),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Create group")
                        }

                        HorizontalDivider()

                        if (groups.isEmpty()) {
                            Text(
                                text = "No channel groups yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(
                                    items = groups,
                                    key = LiveCustomGroup::groupId,
                                ) { group ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = group.name,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    renameTarget = group
                                                    renameValue = group.name
                                                },
                                            ) {
                                                Text("Rename")
                                            }
                                            TextButton(
                                                onClick = { deleteTarget = group },
                                            ) {
                                                Text(
                                                    text = "Delete",
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                }
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
    }
}
