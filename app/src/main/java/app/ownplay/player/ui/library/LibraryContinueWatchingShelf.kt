package app.ownplay.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.ui.vod.RemotePoster
import app.ownplay.player.vod.VodMovie

@Composable
internal fun LibraryContinueWatchingShelf(
    movies: List<VodMovie>,
    onOpenMovie: (VodMovie) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (movies.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 12.dp),
        ) {
            items(movies, key = VodMovie::movieId) { movie ->
                ContinueWatchingMovieCard(
                    movie = movie,
                    onOpen = { onOpenMovie(movie) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingMovieCard(
    movie: VodMovie,
    onOpen: () -> Unit,
) {
    var focused by remember(movie.movieId) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = if (focused) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            RemotePoster(
                url = movie.posterUrl,
                title = movie.name,
                modifier = Modifier
                    .width(58.dp)
                    .aspectRatio(2f / 3f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = libraryResumeLabel(movie),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun libraryResumeLabel(movie: VodMovie): String =
    libraryResumePercent(movie.positionMs, movie.durationMs)
        ?.let { percent -> "Resume · $percent%" }
        ?: "Resume"

internal fun libraryResumePercent(
    positionMs: Long?,
    durationMs: Long?,
): Int? {
    val position = positionMs ?: return null
    val duration = durationMs ?: return null
    if (position <= 0L || duration <= 0L) return null
    val boundedPosition = position.coerceAtMost(duration)
    return ((boundedPosition.toDouble() / duration.toDouble()) * 100.0)
        .toInt()
        .coerceIn(1, 99)
}
