package app.ownplay.player.ui.live

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.live.LiveCustomGroup

private enum class GroupManagerAction {
    RENAME,
    DELETE,
}

private sealed interface GroupManagerFocusRequest {
    data object NewGroup : GroupManagerFocusRequest
    data class Action(
        val groupId: String,
        val action: GroupManagerAction,
    ) : GroupManagerFocusRequest
}

@Composable
fun CustomGroupManagerDialog(
    groups: List<LiveCustomGroup>,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    var newGroupName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LiveCustomGroup?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LiveCustomGroup?>(null) }
    var parentFocusRequest by remember {
        mutableStateOf<GroupManagerFocusRequest?>(GroupManagerFocusRequest.NewGroup)
    }
    val newGroupFocusRequester = remember { FocusRequester() }
    val renameInputFocusRequester = remember { FocusRequester() }
    val deleteCancelFocusRequester = remember { FocusRequester() }
    val groupListState = rememberLazyListState()
    val groupIds = groups.map(LiveCustomGroup::groupId)
    val renameFocusRequesters = remember(groupIds) {
        groupIds.associateWith { FocusRequester() }
    }
    val deleteFocusRequesters = remember(groupIds) {
        groupIds.associateWith { FocusRequester() }
    }

    val groupToRename = renameTarget
    val groupToDelete = deleteTarget

    LaunchedEffect(isTelevision, groupToRename?.groupId) {
        if (isTelevision && groupToRename != null) {
            withFrameNanos { }
            renameInputFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(groupToDelete?.groupId) {
        if (groupToDelete != null) {
            withFrameNanos { }
            deleteCancelFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(
        isTelevision,
        groupToRename?.groupId,
        groupToDelete?.groupId,
        parentFocusRequest,
        groupIds,
    ) {
        val request = parentFocusRequest ?: return@LaunchedEffect
        if (!isTelevision || groupToRename != null || groupToDelete != null) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        val requester = when (request) {
            GroupManagerFocusRequest.NewGroup -> newGroupFocusRequester
            is GroupManagerFocusRequest.Action -> when (request.action) {
                GroupManagerAction.RENAME -> renameFocusRequesters[request.groupId]
                GroupManagerAction.DELETE -> deleteFocusRequesters[request.groupId]
            } ?: newGroupFocusRequester
        }
        requester.requestFocus()
        parentFocusRequest = null
    }

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(renameInputFocusRequester),
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
                title = { Text("Delete group?") },
                text = {
                    Text(
                        "This removes the local group and its memberships. " +
                            "Channels and provider data are not deleted.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val deletedIndex = groups.indexOfFirst {
                                it.groupId == groupToDelete.groupId
                            }
                            val fallbackGroup = groups.getOrNull(deletedIndex + 1)
                                ?: groups.getOrNull(deletedIndex - 1)
                            parentFocusRequest = fallbackGroup?.let { group ->
                                GroupManagerFocusRequest.Action(
                                    groupId = group.groupId,
                                    action = GroupManagerAction.DELETE,
                                )
                            } ?: GroupManagerFocusRequest.NewGroup
                            onDeleteGroup(groupToDelete.groupId)
                            deleteTarget = null
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { deleteTarget = null },
                        modifier = Modifier.focusRequester(deleteCancelFocusRequester),
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        else -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Custom groups") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(newGroupFocusRequester),
                            label = { Text("New group") },
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
                            Text("Create")
                        }

                        HorizontalDivider()

                        if (groups.isEmpty()) {
                            Text("No custom groups yet.")
                        } else {
                            LazyColumn(
                                state = groupListState,
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
                                                    parentFocusRequest = GroupManagerFocusRequest.Action(
                                                        groupId = group.groupId,
                                                        action = GroupManagerAction.RENAME,
                                                    )
                                                    renameTarget = group
                                                    renameValue = group.name
                                                },
                                                modifier = Modifier.focusRequester(
                                                    renameFocusRequesters.getValue(group.groupId),
                                                ),
                                            ) {
                                                Text("Rename")
                                            }
                                            TextButton(
                                                onClick = {
                                                    parentFocusRequest = GroupManagerFocusRequest.Action(
                                                        groupId = group.groupId,
                                                        action = GroupManagerAction.DELETE,
                                                    )
                                                    deleteTarget = group
                                                },
                                                modifier = Modifier.focusRequester(
                                                    deleteFocusRequesters.getValue(group.groupId),
                                                ),
                                            ) {
                                                Text("Delete")
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
