package app.ownplay.player.epg

import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val EPG_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun epgTimeLabel(
    epochSeconds: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? = try {
    EPG_TIME_FORMATTER.format(Instant.ofEpochSecond(epochSeconds).atZone(zoneId))
} catch (_: DateTimeException) {
    null
} catch (_: ArithmeticException) {
    null
}
