package app.ownplay.player.source.network

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpCoroutineTest {
    @Test
    fun cancellationCancelsUnderlyingCall() = runBlocking {
        val call = RecordingCall()
        val job = launch {
            call.awaitResponse()
        }

        yield()
        job.cancelAndJoin()

        assertTrue(call.cancelled.get())
    }

    @Test
    fun lateFailureAfterCancellationIsIgnored() = runBlocking {
        val call = RecordingCall()
        val job = launch {
            call.awaitResponse()
        }

        yield()
        job.cancelAndJoin()
        call.fail(IOException("late network callback"))

        assertTrue(call.cancelled.get())
    }
}

private class RecordingCall : Call {
    val cancelled = AtomicBoolean(false)
    private val request = Request.Builder().url("https://example.com/").build()
    private var callback: Callback? = null

    override fun request(): Request = request

    override fun execute(): Response = throw UnsupportedOperationException("Not used")

    override fun enqueue(responseCallback: Callback) {
        callback = responseCallback
    }

    override fun cancel() {
        cancelled.set(true)
    }

    override fun isExecuted(): Boolean = false

    override fun isCanceled(): Boolean = cancelled.get()

    override fun timeout(): Timeout = Timeout()

    override fun clone(): Call = RecordingCall()

    fun fail(error: IOException) {
        requireNotNull(callback).onFailure(this, error)
    }
}
