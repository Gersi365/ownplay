package app.ownplay.player.epg

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgTimeLabelsTest {
    @Test
    fun formatsNormalEpochInRequestedZone() {
        assertEquals("00:00", epgTimeLabel(0L, ZoneOffset.UTC))
        assertEquals("01:00", epgTimeLabel(3_600L, ZoneOffset.UTC))
    }

    @Test
    fun extremeEpochReturnsNoLabelInsteadOfThrowing() {
        assertNull(epgTimeLabel(Long.MAX_VALUE, ZoneOffset.UTC))
        assertNull(epgTimeLabel(Long.MIN_VALUE, ZoneOffset.UTC))
    }
}
