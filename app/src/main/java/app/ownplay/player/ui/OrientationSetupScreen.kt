package app.ownplay.player.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ownplay.player.personalization.AppDeviceProfile
import app.ownplay.player.personalization.AppOrientationMode

@Composable
internal fun OrientationSetupLoadingSurface() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
internal fun DeviceProfileSetupScreen(
    onConfigured: (profile: AppDeviceProfile, smartphoneOrientation: AppOrientationMode?) -> Unit,
) {
    var selectedProfile by remember { mutableStateOf<AppDeviceProfile?>(null) }
    val smartphoneFocusRequester = remember { FocusRequester() }
    val portraitFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedProfile) {
        if (selectedProfile == AppDeviceProfile.SMARTPHONE) {
            portraitFocusRequester.requestFocus()
        } else if (selectedProfile == null) {
            smartphoneFocusRequester.requestFocus()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val isWideLayout = maxWidth >= 600.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (selectedProfile == AppDeviceProfile.SMARTPHONE) {
                    SmartphoneOrientationStep(
                        portraitFocusRequester = portraitFocusRequester,
                        onBack = { selectedProfile = null },
                        onSelected = { orientation ->
                            onConfigured(AppDeviceProfile.SMARTPHONE, orientation)
                        },
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Choose this touchscreen device",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "OwnPlay Mobile supports Smartphone and Tablet layouts. You can change this later in Settings → Interface.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Touch to choose.",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    val choices = listOf(
                        DeviceProfileChoice(
                            profile = AppDeviceProfile.SMARTPHONE,
                            title = "Smartphone",
                            detail = "Touchscreen · Portrait or Landscape",
                        ),
                        DeviceProfileChoice(
                            profile = AppDeviceProfile.TABLET,
                            title = "Tablet",
                            detail = "Touchscreen · Landscape",
                        ),
                    )

                    if (isWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            choices.forEachIndexed { index, choice ->
                                DeviceProfileChoiceButton(
                                    choice = choice,
                                    focusRequester = if (index == 0) smartphoneFocusRequester else null,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (choice.profile == AppDeviceProfile.SMARTPHONE) {
                                            selectedProfile = AppDeviceProfile.SMARTPHONE
                                        } else {
                                            onConfigured(choice.profile, null)
                                        }
                                    },
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            choices.forEachIndexed { index, choice ->
                                DeviceProfileChoiceButton(
                                    choice = choice,
                                    focusRequester = if (index == 0) smartphoneFocusRequester else null,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        if (choice.profile == AppDeviceProfile.SMARTPHONE) {
                                            selectedProfile = AppDeviceProfile.SMARTPHONE
                                        } else {
                                            onConfigured(choice.profile, null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartphoneOrientationStep(
    portraitFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onSelected: (AppOrientationMode) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Smartphone orientation",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Smartphone can use Portrait or Landscape. Tablet remains Landscape.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupChoiceButton(
                title = "Portrait",
                detail = "Vertical phone layout",
                focusRequester = portraitFocusRequester,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(AppOrientationMode.PORTRAIT) },
            )
            SetupChoiceButton(
                title = "Landscape",
                detail = "Wide phone layout",
                focusRequester = null,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(AppOrientationMode.LANDSCAPE) },
            )
        }
        TextButton(onClick = onBack) {
            Text("‹ Device type")
        }
    }
}

private data class DeviceProfileChoice(
    val profile: AppDeviceProfile,
    val title: String,
    val detail: String,
)

@Composable
private fun DeviceProfileChoiceButton(
    choice: DeviceProfileChoice,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SetupChoiceButton(
        title = choice.title,
        detail = choice.detail,
        focusRequester = focusRequester,
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
private fun SetupChoiceButton(
    title: String,
    detail: String,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val border = BorderStroke(
        width = if (focused) 3.dp else 1.dp,
        color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    )
    OutlinedButton(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer {
                val focusedScale = if (focused) 1.04f else 1f
                scaleX = focusedScale
                scaleY = focusedScale
            }
            .heightIn(min = 112.dp),
        border = border,
        onClick = onClick,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
