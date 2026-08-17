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
)
