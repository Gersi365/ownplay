package app.ownplay.player.source

import app.ownplay.player.PendingImportCoordinator
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingImportCoordinatorTest {
    @Test
    fun duplicateScheduleRunsSourceOnlyOnce() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val coordinator = PendingImportCoordinator(
            scope = this,
            maxConcurrentImports = 2,
        ) {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
        }

        coordinator.schedule("source-a")
        coordinator.schedule("source-a")
        withTimeout(1_000) { started.await() }

        assertEquals(1, calls.get())
        assertTrue(coordinator.isActive("source-a"))
        release.complete(Unit)
        withTimeout(1_000) {
            while (coordinator.isActive("source-a")) delay(10)
        }
        assertFalse(coordinator.isActive("source-a"))
    }

    @Test
    fun concurrencyLimitLetsAnotherSourceRunWithoutGlobalSerialization() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val twoStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = PendingImportCoordinator(
            scope = this,
            maxConcurrentImports = 2,
        ) {
            val now = active.incrementAndGet()
            maxActive.updateAndGet { previous -> maxOf(previous, now) }
            if (now == 2) twoStarted.complete(Unit)
            try {
                release.await()
            } finally {
                active.decrementAndGet()
            }
        }

        coordinator.schedule("source-a")
        coordinator.schedule("source-b")
        coordinator.schedule("source-c")
        withTimeout(1_000) { twoStarted.await() }

        assertEquals(2, maxActive.get())
        release.complete(Unit)
        Unit
    }
}
