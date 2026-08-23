package app.ownplay.player.persistence.secure

@JvmInline
value class SensitiveValueRef(val value: String) {
    init {
        require(value.isNotBlank()) { "Sensitive value reference must not be blank" }
    }

    override fun toString(): String = "SensitiveValueRef(<opaque>)"
}

interface SensitiveValueStore {
    fun put(value: String): SensitiveValueRef

    fun putAll(values: List<String>): List<SensitiveValueRef> {
        if (values.isEmpty()) return emptyList()
        val allocated = mutableListOf<SensitiveValueRef>()
        return try {
            values.mapTo(allocated) { value -> put(value) }
        } catch (error: Exception) {
            allocated.forEach { ref -> runCatching { delete(ref) } }
            throw error
        }
    }

    fun get(ref: SensitiveValueRef): String?

    fun delete(ref: SensitiveValueRef)

    fun deleteAll(refs: Collection<SensitiveValueRef>) {
        refs.forEach(::delete)
    }
}
