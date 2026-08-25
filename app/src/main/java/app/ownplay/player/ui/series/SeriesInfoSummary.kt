package app.ownplay.player.ui.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.series.SeriesDetails
import app.ownplay.player.series.SeriesSummary
import app.ownplay.player.ui.vod.RemotePoster

@Composable
internal fun SeriesInfoSummary(
    selected: SeriesSummary,
    details: SeriesDetails,
) {
    val episodeCount = details.seasons.sumOf { it.episodes.size }
    val metadata = buildList {
        details.rating?.let { add("Rating ${"%.1f".format(it)}") }
        details.releaseDate?.takeIf(String::isNotBlank)?.let(::add)
        details.genre?.takeIf(String::isNotBlank)?.let(::add)
        details.country?.takeIf(String::isNotBlank)?.let(::add)
        if (details.seasons.isNotEmpty()) {
            add("${details.seasons.size} season${if (details.seasons.size == 1) "" else "s"}")
        }
        if (episodeCount > 0) {
            add("$episodeCount episode${if (episodeCount == 1) "" else "s"}")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RemotePoster(
            url = details.posterUrl ?: selected.posterUrl,
            title = selected.name,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(2f / 3f),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            metadata.forEach { item ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            details.director?.takeIf(String::isNotBlank)?.let { director ->
                Text(
                    text = "Director · $director",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            details.cast?.takeIf(String::isNotBlank)?.let { cast ->
                Text(
                    text = "Cast · $cast",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    details.description?.takeIf(String::isNotBlank)?.let { description ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
