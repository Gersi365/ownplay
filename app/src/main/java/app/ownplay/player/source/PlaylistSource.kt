package app.ownplay.player.source

@JvmInline
value class CredentialRef(val value: String) {
    init {
        require(value.isNotBlank()) { "Credential reference must not be blank" }
    }
}

sealed interface PlaylistSource {
    val name: String

    data class Xtream(
        override val name: String,
        val serverUrl: String,
        val credentialRef: CredentialRef,
    ) : PlaylistSource

    data class RemoteM3u(
        override val name: String,
        val playlistUrl: String,
        val epgUrl: String? = null,
    ) : PlaylistSource

    data class LocalM3u(
        override val name: String,
        val documentUri: String,
        val epgUrl: String? = null,
    ) : PlaylistSource
}
