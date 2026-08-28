package app.ownplay.player.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ownplay.player.backup.BackupExportResult
import app.ownplay.player.backup.BackupRestoreFailureReason
import app.ownplay.player.backup.BackupRestoreResult
import app.ownplay.player.backup.PersonalizationBackupService
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BACKUP_CHARS = 5_000_000

private enum class BackupStatusTone {
    PROGRESS,
    SUCCESS,
    ERROR,
}

private data class BackupUiStatus(
    val message: String,
    val tone: BackupStatusTone,
)

@Composable
internal fun BackupRestoreSettingsContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember(context.applicationContext) {
        PersonalizationBackupService(context.applicationContext)
    }
    var status by remember { mutableStateOf<BackupUiStatus?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = BackupUiStatus("Creating backup…", BackupStatusTone.PROGRESS)
            status = when (val result = service.exportBackup()) {
                is BackupExportResult.Success -> {
                    val written = writeBackup(context, uri, result.content)
                    if (!written) {
                        BackupUiStatus(
                            message = "Backup could not be written.",
                            tone = BackupStatusTone.ERROR,
                        )
                    } else {
                        BackupUiStatus(
                            message = buildString {
                                append("Backup saved: ")
                                append(result.channelRecords)
                                append(" channel records, ")
                                append(result.groups)
                                append(" groups, ")
                                append(result.memberships)
                                append(" memberships.")
                                if (result.omittedLogoOverrides > 0) {
                                    append(" ")
                                    append(result.omittedLogoOverrides)
                                    append(" secure logo override(s) were intentionally omitted.")
                                }
                            },
                            tone = BackupStatusTone.SUCCESS,
                        )
                    }
                }
                BackupExportResult.Failure -> BackupUiStatus(
                    message = "Backup export failed safely. No file data was written.",
                    tone = BackupStatusTone.ERROR,
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = BackupUiStatus("Validating backup…", BackupStatusTone.PROGRESS)
            val raw = readBackup(context, uri)
            status = if (raw == null) {
                BackupUiStatus(
                    message = "Backup could not be read or is larger than 5 MB.",
                    tone = BackupStatusTone.ERROR,
                )
            } else {
                restoreStatus(service.restoreBackup(raw))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SettingsActionRow(
            title = "Export personalization",
            detail = "Favorites · order · hidden channels · groups · no credentials",
            actionLabel = "Export",
            onClick = { exportLauncher.launch("ownplay-personalization-v1.json") },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsActionRow(
            title = "Restore personalization",
            detail = "Validated before apply · existing data kept · unmatched items skipped",
            actionLabel = "Import",
            onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain"))
            },
        )
        status?.let { current ->
            Text(
                text = current.message,
                style = MaterialTheme.typography.labelSmall,
                color = backupStatusColor(current.tone),
            )
        }
    }
}

@Composable
private fun backupStatusColor(tone: BackupStatusTone): Color = when (tone) {
    BackupStatusTone.PROGRESS -> MaterialTheme.colorScheme.onSurfaceVariant
    BackupStatusTone.SUCCESS -> MaterialTheme.colorScheme.primary
    BackupStatusTone.ERROR -> MaterialTheme.colorScheme.error
}

private suspend fun writeBackup(
    context: Context,
    uri: Uri,
    content: String,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
        stream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(content) }
        true
    } catch (_: Exception) {
        false
    }
}

private suspend fun readBackup(
    context: Context,
    uri: Uri,
): String? = withContext(Dispatchers.IO) {
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val output = StringBuilder()
            val buffer = CharArray(8192)
            var total = 0
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BACKUP_CHARS) throw IOException("Backup exceeds size limit")
                output.append(buffer, 0, read)
            }
            output.toString()
        }
    } catch (_: Exception) {
        null
    }
}

private fun restoreStatus(result: BackupRestoreResult): BackupUiStatus = when (result) {
    is BackupRestoreResult.Success -> BackupUiStatus(
        message = buildString {
            append("Restore complete: ")
            append(result.appliedChannelRecords)
            append(" channel records, ")
            append(result.appliedGroups)
            append(" groups, ")
            append(result.appliedMemberships)
            append(" memberships applied.")
            if (result.unmatchedChannelIdentities > 0 || result.ambiguousChannelIdentities > 0) {
                append(" Skipped identities: ")
                append(result.unmatchedChannelIdentities)
                append(" unmatched, ")
                append(result.ambiguousChannelIdentities)
                append(" ambiguous.")
            }
            if (result.omittedLogoOverrides > 0) {
                append(" ")
                append(result.omittedLogoOverrides)
                append(" secure logo override(s) were not restored.")
            }
        },
        tone = BackupStatusTone.SUCCESS,
    )
    is BackupRestoreResult.Failure -> BackupUiStatus(
        message = when (result.reason) {
            BackupRestoreFailureReason.INVALID_JSON -> "Restore rejected: file is not valid JSON."
            BackupRestoreFailureReason.UNSUPPORTED_FORMAT -> "Restore rejected: unsupported backup format."
            BackupRestoreFailureReason.UNSUPPORTED_VERSION -> "Restore rejected: unsupported backup version."
            BackupRestoreFailureReason.INVALID_PAYLOAD -> "Restore rejected: backup payload is invalid."
            BackupRestoreFailureReason.PERSISTENCE_FAILURE ->
                "Restore failed safely while applying data; the database transaction was rolled back."
        },
        tone = BackupStatusTone.ERROR,
    )
}
