package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshTimeLabelsTest {
    @Test
    fun invalidOrMissingTimestampIsNotReportedAsRefreshed() {
        assertEquals("Not refreshed yet", refreshTimeLabel(null))
        assertEquals("Not refreshed yet", refreshTimeLabel(0L))
        assertEquals("Not refreshed yet", refreshTimeLabel(-1L))
    }
}
