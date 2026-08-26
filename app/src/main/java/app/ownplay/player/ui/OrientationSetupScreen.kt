package app.ownplay.player.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
internal fun OrientationSetupScreen(
    onOrientationSelected: (AppOrientationMode) -> Unit,
) {
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
                    .widthIn(max = 760.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Choose app orientation",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Choose how OwnPlay should fit this device. You can change this later in Settings → Interface.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                if (isWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OrientationChoiceButton(
                            title = "Portrait",
                            detail = "Phones and vertical displays",
                            modifier = Modifier.weight(1f),
                            onClick = { onOrientationSelected(AppOrientationMode.PORTRAIT) },
                        )
                        OrientationChoiceButton(
                            title = "Landscape",
                            detail = "TVs, tablets and wide displays",
                            modifier = Modifier.weight(1f),
                            onClick = { onOrientationSelected(AppOrientationMode.LANDSCAPE) },
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OrientationChoiceButton(
                            title = "Portrait",
                            detail = "Phones and vertical displays",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOrientationSelected(AppOrientationMode.PORTRAIT) },
                        )
                        OrientationChoiceButton(
                            title = "Landscape",
                            detail = "TVs, tablets and wide displays",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOrientationSelected(AppOrientationMode.LANDSCAPE) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrientationChoiceButton(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier.heightIn(min = 112.dp),
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
