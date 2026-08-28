package app.ownplay.player.source.network

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        cancel()
    }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                continuation.tryResumeWithException(error)?.let(continuation::completeResume)
            }

            override fun onResponse(call: Call, response: Response) {
                val token = continuation.tryResume(response)
                if (token == null) {
                    response.close()
                } else {
                    continuation.completeResume(token)
                }
            }
        },
    )
}
