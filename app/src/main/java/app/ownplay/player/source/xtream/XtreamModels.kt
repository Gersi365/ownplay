package app.ownplay.player.source.xtream

data class XtreamAccountInfo(
    val status: String?,
    val expiresAtEpochSeconds: Long?,
    val maxConnections: Int?,
    val isTrial: Boolean?,
    val allowedOutputFormats: List<String>,
    val serverInfo: XtreamServerInfo?,
)

data class XtreamServerInfo(
    val protocol: String?,
    val timezone: String?,
)

data class XtreamCategory(
    val id: String,
    val name: String,
    val parentId: String?,
)

data class XtreamLiveStream(
    val streamId: Int,
    val name: String,
    val categoryId: String?,
    val iconUrl: String?,
    val epgChannelId: String?,
    val archiveDurationDays: Int?,
    val directSource: String?,
) {
    override fun toString(): String =
        "XtreamLiveStream(streamId=$streamId, name=$name, categoryId=$categoryId, " +
            "iconUrl=${redacted(iconUrl)}, epgChannelId=$epgChannelId, " +
            "archiveDurationDays=$archiveDurationDays, directSource=${redacted(directSource)})"
}

private fun redacted(value: String?): String = if (value == null) "null" else "<redacted>"
