package app.ownplay.player.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.ownplay.player.download.OfflineDownload

@Composable
internal fun DownloadRemovalConfirmationDialog(
    download: OfflineDownload,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove download?") },
        text = {
            Text(
                "OwnPlay will cancel any active transfer, remove “${download.title}” from " +
                    "Library, and delete its offline file from this device. You can download it again later.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
