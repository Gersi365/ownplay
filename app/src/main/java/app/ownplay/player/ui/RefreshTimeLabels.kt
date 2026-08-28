package app.ownplay.player.ui

import java.text.DateFormat
import java.util.Date

internal fun refreshTimeLabel(timestampEpochMillis: Long?): String {
    val timestamp = timestampEpochMillis?.takeIf { it > 0L } ?: return "Not refreshed yet"
    return DateFormat
        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestamp))
}
