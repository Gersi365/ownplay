package app.ownplay.player.source

import app.ownplay.player.PendingImportCoordinator
import app.ownplay.player.PendingImportExecutionTracker
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
        assertTrue("source-a" in PendingImportExecutionTracker.state.value.activeSourceIds)
        release.complete(Unit)
        withTimeout(1_000) {
            while (coordinator.isActive("source-a")) delay(10)
        }
        assertFalse(coordinator.isActive("source-a"))
        assertFalse("source-a" in PendingImportExecutionTracker.state.value.activeSourceIds)
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

    @Test
    fun queuedSourceIsTrackedSeparatelyAndCancelClearsIt() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val coordinator = PendingImportCoordinator(
            scope = this,
            maxConcurrentImports = 1,
        ) { sourceId ->
            if (sourceId == "source-a") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }

        coordinator.schedule("source-a")
        withTimeout(1_000) { firstStarted.await() }
        coordinator.schedule("source-b")

        withTimeout(1_000) {
            while ("source-b" !in PendingImportExecutionTracker.state.value.queuedSourceIds) {
                delay(10)
            }
        }
        val waitingState = PendingImportExecutionTracker.state.value
        assertTrue("source-a" in waitingState.activeSourceIds)
        assertTrue("source-b" in waitingState.queuedSourceIds)
        assertFalse("source-b" in waitingState.activeSourceIds)

        coordinator.cancel("source-b")
        assertFalse("source-b" in PendingImportExecutionTracker.state.value.queuedSourceIds)
        assertFalse("source-b" in PendingImportExecutionTracker.state.value.activeSourceIds)

        releaseFirst.complete(Unit)
        withTimeout(1_000) {
            while (coordinator.isActive("source-a")) delay(10)
        }
    }
}
