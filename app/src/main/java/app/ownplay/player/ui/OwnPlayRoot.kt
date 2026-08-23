package app.ownplay.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.playback.PlaybackState
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.onboarding.SourceOnboardingFailure
import app.ownplay.player.source.onboarding.SourceOnboardingResult
import kotlinx.coroutines.launch

private enum class SourceOnboardingMode {
    XTREAM,
    REMOTE_M3U,
    LOCAL_M3U,
}

@Composable
fun OwnPlayRoot(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit = {},
) {
    val sources by runtime.observeSources().collectAsState(initial = emptyList())
    val playbackState by runtime.playbackController.state.collectAsState()
    var showOnboarding by remember { mutableStateOf(false) }

    if (sources.isEmpty() || showOnboarding) {
        SourceOnboardingScreen(
            runtime = runtime,
            canCancel = sources.isNotEmpty(),
            onCompleted = { showOnboarding = false },
            onCancel = { showOnboarding = false },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OwnPlayApp(
            runtime = runtime,
            onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
        )
        if (playbackState is PlaybackState.Idle) {
            ExtendedFloatingActionButton(
                onClick = { showOnboarding = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Text("Add source")
            }
        }
    }
}

@Composable
private fun SourceOnboardingScreen(
    runtime: OwnPlayAppRuntime,
    canCancel: Boolean,
    onCompleted: () -> Unit,
    onCancel: () -> Unit,
) {
    var mode by remember { mutableStateOf<SourceOnboardingMode?>(null) }

    BackHandler(enabled = mode != null || canCancel) {
        if (mode != null) {
            mode = null
        } else {
            onCancel()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (mode != null || canCancel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mode != null) {
                        TextButton(onClick = { mode = null }) {
                            Text("Back")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (canCancel) {
                        TextButton(onClick = onCancel) {
                            Text("Cancel")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Add playlist",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Use a media source you are authorized to access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            when (mode) {
                null -> SourceMethodChooser(onSelected = { mode = it })
                SourceOnboardingMode.XTREAM -> XtreamSourceForm(
                    runtime = runtime,
                    onCompleted = onCompleted,
                )
                SourceOnboardingMode.REMOTE_M3U -> RemoteM3uSourceForm(
                    runtime = runtime,
                    onCompleted = onCompleted,
                )
                SourceOnboardingMode.LOCAL_M3U -> LocalM3uSourceForm(
                    runtime = runtime,
                    onCompleted = onCompleted,
                )
            }
        }
    }
}

@Composable
private fun SourceMethodChooser(
    onSelected: (SourceOnboardingMode) -> Unit,
) {
    Text(
        text = "Choose source type",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
    )
    SourceMethodButton(
        title = "Xtream",
        subtitle = "Server URL, username and password",
        onClick = { onSelected(SourceOnboardingMode.XTREAM) },
    )
    SourceMethodButton(
        title = "M3U URL",
        subtitle = "Import a remote M3U or M3U8 playlist",
        onClick = { onSelected(SourceOnboardingMode.REMOTE_M3U) },
    )
    SourceMethodButton(
        title = "Local M3U file",
        subtitle = "Choose a playlist file stored on this device",
        onClick = { onSelected(SourceOnboardingMode.LOCAL_M3U) },
    )
}

@Composable
private fun SourceMethodButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun XtreamSourceForm(
    runtime: OwnPlayAppRuntime,
    onCompleted: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Text(
        text = "Xtream",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Playlist name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text("Server URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    SourceFormError(errorMessage)
    SubmitButton(
        label = "Connect",
        submitting = submitting,
        onClick = {
            if (submitting) return@SubmitButton
            submitting = true
            errorMessage = null
            scope.launch {
                when (
                    val result = runtime.addXtreamSource(
                        name = name,
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                    )
                ) {
                    is SourceOnboardingResult.Success -> {
                        password = ""
                        onCompleted()
                    }
                    is SourceOnboardingResult.Failure -> {
                        password = ""
                        errorMessage = onboardingFailureMessage(result.reason)
                        submitting = false
                    }
                }
            }
        },
    )
}

@Composable
private fun RemoteM3uSourceForm(
    runtime: OwnPlayAppRuntime,
    onCompleted: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Text(
        text = "M3U URL",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Playlist name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = playlistUrl,
        onValueChange = { playlistUrl = it },
        label = { Text("Playlist URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SourceFormError(errorMessage)
    SubmitButton(
        label = "Connect",
        submitting = submitting,
        onClick = {
            if (submitting) return@SubmitButton
            submitting = true
            errorMessage = null
            scope.launch {
                when (
                    val result = runtime.addRemoteM3uSource(
                        name = name,
                        playlistUrl = playlistUrl,
                    )
                ) {
                    is SourceOnboardingResult.Success -> onCompleted()
                    is SourceOnboardingResult.Failure -> {
                        errorMessage = onboardingFailureMessage(result.reason)
                        submitting = false
                    }
                }
            }
        },
    )
}

@Composable
private fun LocalM3uSourceForm(
    runtime: OwnPlayAppRuntime,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var selectedDocumentUri by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val permissionRetained = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
        if (!permissionRetained) {
            selectedDocumentUri = null
            selectedFileName = null
            errorMessage = "OwnPlay could not retain access to this file."
            return@rememberLauncherForActivityResult
        }
        selectedDocumentUri = uri.toString()
        selectedFileName = displayNameForUri(context, uri)
        if (name.isBlank()) {
            name = selectedFileName
                ?.substringBeforeLast('.', missingDelimiterValue = selectedFileName.orEmpty())
                ?.takeIf(String::isNotBlank)
                ?: "Local playlist"
        }
        errorMessage = null
    }

    Text(
        text = "Local M3U file",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Medium,
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Playlist name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { documentPicker.launch(arrayOf("*/*")) },
        enabled = !submitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (selectedDocumentUri == null) "Choose M3U file" else "Choose another file")
    }
    selectedFileName?.let { fileName ->
        Text(
            text = "Selected: $fileName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SourceFormError(errorMessage)
    SubmitButton(
        label = "Import",
        submitting = submitting,
        enabled = selectedDocumentUri != null,
        onClick = {
            val documentUri = selectedDocumentUri ?: return@SubmitButton
            if (submitting) return@SubmitButton
            submitting = true
            errorMessage = null
            scope.launch {
                when (
                    val result = runtime.addLocalM3uSource(
                        name = name,
                        documentUri = documentUri,
                    )
                ) {
                    is SourceOnboardingResult.Success -> onCompleted()
                    is SourceOnboardingResult.Failure -> {
                        errorMessage = onboardingFailureMessage(result.reason)
                        submitting = false
                    }
                }
            }
        },
    )
}

@Composable
private fun SubmitButton(
    label: String,
    submitting: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !submitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (submitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(if (submitting) "Working…" else label)
    }
}

@Composable
private fun SourceFormError(message: String?) {
    message?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun onboardingFailureMessage(failure: SourceOnboardingFailure): String = when (failure) {
    SourceOnboardingFailure.InvalidName -> "Enter a playlist name."
    SourceOnboardingFailure.SecureStorageFailure ->
        "Secure storage is unavailable. The source was not saved."
    SourceOnboardingFailure.PersistenceFailure ->
        "OwnPlay could not save this source."
    SourceOnboardingFailure.CatalogImportFailure ->
        "The source connected, but its channel catalog could not be imported."
    is SourceOnboardingFailure.SourceFailure -> sourceErrorMessage(failure.error)
}

private fun sourceErrorMessage(error: SourceError): String = when (error) {
    SourceError.EmptyValue -> "Enter all required values."
    SourceError.InvalidUrl,
    SourceError.UnsupportedScheme,
    SourceError.MissingHost,
    SourceError.UnexpectedUrlComponent,
    -> "Enter a valid source URL."
    SourceError.EmbeddedCredentialsNotAllowed ->
        "Do not put usernames or passwords inside the URL."
    SourceError.UnsupportedLocalUri -> "Choose a supported local playlist file."
    SourceError.InvalidCredentials,
    SourceError.CredentialUnavailable,
    SourceError.AuthenticationFailed,
    -> "The source rejected the supplied credentials."
    SourceError.CleartextTransportRequiresOptIn ->
        "This build requires HTTPS for remote sources."
    SourceError.SecureConnectionFailed -> "A secure connection could not be established."
    SourceError.NetworkUnavailable -> "The source could not be reached. Check your network."
    SourceError.Timeout -> "The source did not respond in time."
    SourceError.SourceReadFailed -> "OwnPlay could not read the selected playlist."
    is SourceError.HttpFailure -> "The source returned an HTTP error."
    SourceError.MalformedResponse -> "The source returned an unsupported response."
    SourceError.MalformedPlaylist -> "The playlist contains no usable channels."
    SourceError.Unknown -> "The source could not be added."
}

private fun displayNameForUri(
    context: Context,
    uri: Uri,
): String? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)
    }
}.getOrNull()
