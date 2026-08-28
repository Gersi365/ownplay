package app.ownplay.player.persistence.secure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SensitiveValueStoreCleanupTest {
    @Test
    fun deleteAllAttemptsEveryReferenceBeforeRethrowing() {
        val deleted = mutableListOf<String>()
        val store = object : SensitiveValueStore {
            override fun put(value: String): SensitiveValueRef = error("not used")

            override fun get(ref: SensitiveValueRef): String? = error("not used")

            override fun delete(ref: SensitiveValueRef) {
                deleted += ref.value
                if (ref.value == "first") error("first delete failed")
            }
        }

        val failure = assertFailsWith<IllegalStateException> {
            store.deleteAll(
                listOf(
                    SensitiveValueRef("first"),
                    SensitiveValueRef("second"),
                    SensitiveValueRef("third"),
                ),
            )
        }

        assertEquals("first delete failed", failure.message)
        assertEquals(listOf("first", "second", "third"), deleted)
    }
}
