package app.ownplay.player.ui

import androidx.compose.ui.graphics.ImageBitmap
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

private const val MAX_REMOTE_IMAGE_CACHE_BYTES = 16 * 1024 * 1024

internal data class CachedRemoteImage(
    val image: ImageBitmap,
    val byteCount: Int,
)

/**
 * Small process-local cache used by remote artwork surfaces.
 *
 * The cache is intentionally bounded by decoded bitmap weight rather than entry count. Requests
 * for the same key share one in-flight load, and caller cancellation does not cancel that shared
 * load for other visible consumers.
 */
internal class BoundedAsyncCache<K : Any, V : Any>(
    private val maxWeight: Int,
    private val weightOf: (V) -> Int,
    private val loadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private data class WeightedValue<V>(
        val value: V,
        val weight: Int,
    )

    private val lock = Any()
    private val values = LinkedHashMap<K, WeightedValue<V>>(16, 0.75f, true)
    private val inFlight = mutableMapOf<K, Deferred<V?>>()
    private var totalWeight = 0

    init {
        require(maxWeight > 0) { "maxWeight must be positive" }
    }

    suspend fun getOrLoad(
        key: K,
        loader: suspend () -> V?,
    ): V? {
        val deferred = synchronized(lock) {
            values[key]?.let { cached -> return cached.value }
            inFlight[key] ?: loadScope.async(start = CoroutineStart.LAZY) {
                try {
                    loader()?.also { loaded ->
                        synchronized(lock) {
                            putLocked(key, loaded)
                        }
                    }
                } finally {
                    synchronized(lock) {
                        inFlight.remove(key)
                    }
                }
            }.also { created ->
                inFlight[key] = created
                created.start()
            }
        }
        return deferred.await()
    }

    private fun putLocked(key: K, value: V) {
        val weight = weightOf(value).coerceAtLeast(1)
        values.remove(key)?.let { existing ->
            totalWeight -= existing.weight
        }
        if (weight > maxWeight) return

        values[key] = WeightedValue(value = value, weight = weight)
        totalWeight += weight

        val iterator = values.entries.iterator()
        while (totalWeight > maxWeight && iterator.hasNext()) {
            val eldest = iterator.next()
            totalWeight -= eldest.value.weight
            iterator.remove()
        }
    }
}

internal object RemoteImageMemoryCache {
    private val cache = BoundedAsyncCache<String, CachedRemoteImage>(
        maxWeight = MAX_REMOTE_IMAGE_CACHE_BYTES,
        weightOf = CachedRemoteImage::byteCount,
    )

    suspend fun getOrLoad(
        cacheKey: String,
        loader: suspend () -> CachedRemoteImage?,
    ): CachedRemoteImage? = cache.getOrLoad(cacheKey, loader)
}
