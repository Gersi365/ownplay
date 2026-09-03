package app.ownplay.player.ui

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedAsyncCacheTest {
    @Test
    fun cachedValueSkipsRepeatedLoaderWork() = runBlocking {
        val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = AtomicInteger(0)
            val cache = BoundedAsyncCache<String, String>(
                maxWeight = 4,
                weightOf = { 1 },
                loadScope = loadScope,
            )

            assertEquals("first", cache.getOrLoad("poster") {
                calls.incrementAndGet()
                "first"
            })
            assertEquals("first", cache.getOrLoad("poster") {
                calls.incrementAndGet()
                "second"
            })
            assertEquals(1, calls.get())
        } finally {
            loadScope.cancel()
        }
    }

    @Test
    fun leastRecentlyUsedEntryIsEvictedByWeight() = runBlocking {
        val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = mutableMapOf<String, Int>()
            val cache = BoundedAsyncCache<String, String>(
                maxWeight = 2,
                weightOf = { 1 },
                loadScope = loadScope,
            )

            suspend fun load(key: String): String = cache.getOrLoad(key) {
                calls[key] = calls.getOrDefault(key, 0) + 1
                "$key-${calls.getValue(key)}"
            } ?: error("Expected value")

            assertEquals("A-1", load("A"))
            assertEquals("B-1", load("B"))
            assertEquals("A-1", load("A"))
            assertEquals("C-1", load("C"))
            assertEquals("A-1", load("A"))
            assertEquals("B-2", load("B"))

            assertEquals(1, calls.getValue("A"))
            assertEquals(2, calls.getValue("B"))
            assertEquals(1, calls.getValue("C"))
        } finally {
            loadScope.cancel()
        }
    }

    @Test
    fun concurrentCallersShareOneInFlightLoad() = runBlocking {
        val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger(0)
            val cache = BoundedAsyncCache<String, String>(
                maxWeight = 4,
                weightOf = { 1 },
                loadScope = loadScope,
            )

            val first = async {
                cache.getOrLoad("same") {
                    calls.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    "shared"
                }
            }
            started.await()
            val second = async {
                cache.getOrLoad("same") {
                    calls.incrementAndGet()
                    "duplicate"
                }
            }
            yield()

            assertEquals(1, calls.get())
            release.complete(Unit)
            assertEquals("shared", first.await())
            assertEquals("shared", second.await())
            assertEquals(1, calls.get())
        } finally {
            loadScope.cancel()
        }
    }

    @Test
    fun cancellingOneCallerDoesNotCancelSharedLoad() = runBlocking {
        val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val calls = AtomicInteger(0)
            val cache = BoundedAsyncCache<String, String>(
                maxWeight = 4,
                weightOf = { 1 },
                loadScope = loadScope,
            )

            val first = async {
                cache.getOrLoad("same") {
                    calls.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    "survived"
                }
            }
            started.await()
            val second = async {
                cache.getOrLoad("same") {
                    calls.incrementAndGet()
                    "duplicate"
                }
            }
            yield()
            first.cancel()
            release.complete(Unit)

            assertEquals("survived", second.await())
            assertEquals(1, calls.get())
        } finally {
            loadScope.cancel()
        }
    }

    @Test
    fun oversizedValueIsReturnedButNotRetained() = runBlocking {
        val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = AtomicInteger(0)
            val cache = BoundedAsyncCache<String, String>(
                maxWeight = 2,
                weightOf = { 3 },
                loadScope = loadScope,
            )

            assertEquals("value-1", cache.getOrLoad("huge") {
                "value-${calls.incrementAndGet()}"
            })
            assertEquals("value-2", cache.getOrLoad("huge") {
                "value-${calls.incrementAndGet()}"
            })
            assertEquals(2, calls.get())
        } finally {
            loadScope.cancel()
        }
    }
}
