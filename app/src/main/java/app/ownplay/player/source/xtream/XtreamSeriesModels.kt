package app.ownplay.player.source.xtream

data class XtreamSeriesSummary(
    val seriesId: Int,
    val name: String,
    val categoryId: String?,
    val posterUrl: String?,
    val rating: Double?,
    val lastModifiedEpochSeconds: Long?,
    val description: String?,
) {
    override fun toString(): String =
        "XtreamSeriesSummary(seriesId=$seriesId, name=$name, categoryId=$categoryId, " +
            "posterUrl=${redacted(posterUrl)}, rating=$rating, " +
            "lastModifiedEpochSeconds=$lastModifiedEpochSeconds, description=${redacted(description)})"
}

data class XtreamSeriesSeason(
    val seasonNumber: Int,
    val name: String?,
    val airDate: String?,
    val posterUrl: String?,
) {
    override fun toString(): String =
        "XtreamSeriesSeason(seasonNumber=$seasonNumber, name=$name, airDate=$airDate, " +
            "posterUrl=${redacted(posterUrl)})"
}

data class XtreamSeriesEpisode(
    val episodeId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String?,
    val durationSeconds: Long?,
    val description: String?,
    val posterUrl: String?,
    val rating: Double?,
    val addedAtEpochSeconds: Long?,
) {
    override fun toString(): String =
        "XtreamSeriesEpisode(episodeId=$episodeId, seasonNumber=$seasonNumber, " +
            "episodeNumber=$episodeNumber, title=$title, containerExtension=$containerExtension, " +
            "durationSeconds=$durationSeconds, description=${redacted(description)}, " +
            "posterUrl=${redacted(posterUrl)}, rating=$rating, " +
            "addedAtEpochSeconds=$addedAtEpochSeconds)"
}

data class XtreamSeriesInfo(
    val seriesId: Int,
    val name: String?,
    val description: String?,
    val posterUrl: String?,
    val backdropUrls: List<String>,
    val releaseDate: String?,
    val genre: String?,
    val country: String?,
    val director: String?,
    val cast: String?,
    val rating: Double?,
    val seasons: List<XtreamSeriesSeason>,
    val episodes: List<XtreamSeriesEpisode>,
) {
    override fun toString(): String =
        "XtreamSeriesInfo(seriesId=$seriesId, name=$name, description=${redacted(description)}, " +
            "posterUrl=${redacted(posterUrl)}, backdropUrls=<${backdropUrls.size} refs>, " +
            "releaseDate=$releaseDate, genre=$genre, country=$country, director=$director, " +
            "cast=${redacted(cast)}, rating=$rating, seasons=<${seasons.size} items>, " +
            "episodes=<${episodes.size} items>)"
}

private fun redacted(value: String?): String = if (value == null) "null" else "<redacted>"
