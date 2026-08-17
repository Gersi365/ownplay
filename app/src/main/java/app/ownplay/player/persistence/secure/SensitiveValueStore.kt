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
    fun get(ref: SensitiveValueRef): String?
    fun delete(ref: SensitiveValueRef)
}
