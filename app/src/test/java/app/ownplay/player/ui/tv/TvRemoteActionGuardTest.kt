package app.ownplay.player.ui.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRemoteActionGuardTest {
    @Test
    fun standardActionsRejectRapidRepeatedActivation() {
        val guard = TvRemoteActionGuard()

        assertTrue(guard.tryAcquire(1_000L))
        assertFalse(guard.tryAcquire(1_399L))
        assertTrue(guard.tryAcquire(1_400L))
    }

    @Test
    fun transitionActionsHoldLongerLockAcrossDifferentCommands() {
        val guard = TvRemoteActionGuard()

        assertTrue(guard.tryAcquire(2_000L, TvRemoteActionKind.TRANSITION))
        assertFalse(guard.tryAcquire(2_899L, TvRemoteActionKind.STANDARD))
        assertTrue(guard.tryAcquire(2_900L, TvRemoteActionKind.STANDARD))
    }

    @Test
    fun lockDeadlineSaturatesInsteadOfOverflowing() {
        val guard = TvRemoteActionGuard()

        assertTrue(guard.tryAcquire(Long.MAX_VALUE - 100L, TvRemoteActionKind.TRANSITION))
        assertFalse(guard.tryAcquire(Long.MAX_VALUE - 1L))
    }
}
