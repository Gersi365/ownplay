package app.ownplay.player.playback

import app.ownplay.player.persistence.secure.SensitiveValueRef
import app.ownplay.player.persistence.secure.SensitiveValueStore
import app.ownplay.player.source.CredentialRef
import app.ownplay.player.source.SourceValidator
import app.ownplay.player.source.UrlValidationResult
import app.ownplay.player.source.credential.CredentialStore
import java.util.concurrent.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class PlaybackResolutionSourceKind {
    XTREAM,
    OTHER,
}

data class PlaybackSourceRecord(
    val sourceId: String,
    val sourceKind: PlaybackResolutionSourceKind,
    val locatorRef: String,
    val credentialRef: String?,
    val enabled: Boolean,
) {
    override fun toString(): String =
        "PlaybackSourceRecord(sourceId=<opaque>, sourceKind=$sourceKind, locatorRef=<opaque>, " +
            "credentialRef=${if (credentialRef == null) "null" else "<opaque>"}, enabled=$enabled)"
}

data class PlaybackChannelRecord(
    val channelId: String,
    val sourceId: String,
    val streamLocatorRef: String,
    val removed: Boolean,
) {
    override fun toString(): String =
        "PlaybackChannelRecord(channelId=<opaque>, sourceId=<opaque>, streamLocatorRef=<opaque>, removed=$removed)"
}

interface PlaybackResolutionLookup {
    suspend fun sourceById(sourceId: String): PlaybackSourceRecord?
    suspend fun channelById(channelId: String): PlaybackChannelRecord?
}

enum class ResolvedPlaybackOrigin {
    DIRECT,
    XTREAM_LIVE,
}

data class ResolvedPlaybackLocator(
    val value: String,
    val origin: ResolvedPlaybackOrigin,
) {
    init {
        require(value.isNotBlank()) { "Resolved playback locator must not be blank" }
    }

    override fun toString(): String =
        "ResolvedPlaybackLocator(value=<redacted>, origin=$origin)"
}

enum class PlaybackResolutionFailureReason {
    SOURCE_NOT_FOUND,
    SOURCE_DISABLED,
    CHANNEL_NOT_FOUND,
    SOURCE_CHANNEL_MISMATCH,
    CHANNEL_REMOVED,
    DESCRIPTOR_REFERENCE_INVALID,
    DESCRIPTOR_NOT_FOUND,
    DESCRIPTOR_INVALID,
    SECURE_STORE_FAILURE,
    UNSUPPORTED_SOURCE_KIND,
    SOURCE_LOCATOR_REFERENCE_INVALID,
    SOURCE_LOCATOR_NOT_FOUND,
    SOURCE_LOCATOR_INVALID,
    CREDENTIAL_REFERENCE_MISSING,
    CREDENTIAL_REFERENCE_INVALID,
    CREDENTIALS_NOT_FOUND,
    CREDENTIALS_INVALID,
    CREDENTIAL_STORE_FAILURE,
    CLEARTEXT_NOT_ALLOWED,
    PERSISTENCE_FAILURE,
}

sealed interface PlaybackResolutionResult {
    data class Success(
        val locator: ResolvedPlaybackLocator,
    ) : PlaybackResolutionResult

    data class Failure(
        val reason: PlaybackResolutionFailureReason,
    ) : PlaybackResolutionResult
}

class LivePlaybackResolver(
    private val lookup: PlaybackResolutionLookup,
    private val sensitiveValueStore: SensitiveValueStore,
    private val credentialStore: CredentialStore,
    private val allowCleartext: Boolean = false,
) {
    suspend fun resolve(request: PlaybackRequest): PlaybackResolutionResult {
        val source = try {
            lookup.sourceById(request.sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failure(PlaybackResolutionFailureReason.PERSISTENCE_FAILURE)
        } ?: return failure(PlaybackResolutionFailureReason.SOURCE_NOT_FOUND)

        if (!source.enabled) {
            return failure(PlaybackResolutionFailureReason.SOURCE_DISABLED)
        }

        val channel = try {
            lookup.channelById(request.channelId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failure(PlaybackResolutionFailureReason.PERSISTENCE_FAILURE)
        } ?: return failure(PlaybackResolutionFailureReason.CHANNEL_NOT_FOUND)

        if (channel.sourceId != source.sourceId) {
            return failure(PlaybackResolutionFailureReason.SOURCE_CHANNEL_MISMATCH)
        }
        if (channel.removed) {
            return failure(PlaybackResolutionFailureReason.CHANNEL_REMOVED)
        }

        val descriptorRef = sensitiveRef(channel.streamLocatorRef)
            ?: return failure(PlaybackResolutionFailureReason.DESCRIPTOR_REFERENCE_INVALID)
        val descriptor = try {
            sensitiveValueStore.get(descriptorRef)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failure(PlaybackResolutionFailureReason.SECURE_STORE_FAILURE)
        } ?: return failure(PlaybackResolutionFailureReason.DESCRIPTOR_NOT_FOUND)

        return when (val parsed = PlaybackLocatorParser.parse(descriptor)) {
            is PlaybackLocatorParseResult.Failure -> failure(PlaybackResolutionFailureReason.DESCRIPTOR_INVALID)
            is PlaybackLocatorParseResult.Success -> when (val locator = parsed.locator) {
                is ParsedPlaybackLocator.Direct -> resolveDirect(locator.locator)
                is ParsedPlaybackLocator.XtreamLive -> resolveXtream(source, locator.streamId)
            }
        }
    }

    private fun resolveDirect(locator: String): PlaybackResolutionResult {
        return when (val validation = SourceValidator.validateRemotePlaylistUrl(locator)) {
            is UrlValidationResult.Invalid -> failure(PlaybackResolutionFailureReason.DESCRIPTOR_INVALID)
            is UrlValidationResult.Valid -> {
                if (validation.usesCleartext && !allowCleartext) {
                    failure(PlaybackResolutionFailureReason.CLEARTEXT_NOT_ALLOWED)
                } else {
                    success(validation.normalizedUrl, ResolvedPlaybackOrigin.DIRECT)
                }
            }
        }
    }

    private fun resolveXtream(
        source: PlaybackSourceRecord,
        streamId: Int,
    ): PlaybackResolutionResult {
        if (source.sourceKind != PlaybackResolutionSourceKind.XTREAM) {
            return failure(PlaybackResolutionFailureReason.UNSUPPORTED_SOURCE_KIND)
        }

        val sourceLocatorRef = sensitiveRef(source.locatorRef)
            ?: return failure(PlaybackResolutionFailureReason.SOURCE_LOCATOR_REFERENCE_INVALID)
        val serverUrl = try {
            sensitiveValueStore.get(sourceLocatorRef)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failure(PlaybackResolutionFailureReason.SECURE_STORE_FAILURE)
        } ?: return failure(PlaybackResolutionFailureReason.SOURCE_LOCATOR_NOT_FOUND)

        val validatedServer = when (val validation = SourceValidator.validateXtreamServer(serverUrl)) {
            is UrlValidationResult.Invalid -> {
                return failure(PlaybackResolutionFailureReason.SOURCE_LOCATOR_INVALID)
            }
            is UrlValidationResult.Valid -> validation
        }
        if (validatedServer.usesCleartext && !allowCleartext) {
            return failure(PlaybackResolutionFailureReason.CLEARTEXT_NOT_ALLOWED)
        }

        val credentialRefValue = source.credentialRef
            ?: return failure(PlaybackResolutionFailureReason.CREDENTIAL_REFERENCE_MISSING)
        val credentialRef = try {
            CredentialRef(credentialRefValue)
        } catch (_: IllegalArgumentException) {
            return failure(PlaybackResolutionFailureReason.CREDENTIAL_REFERENCE_INVALID)
        }
        val credentials = try {
            credentialStore.get(credentialRef)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failure(PlaybackResolutionFailureReason.CREDENTIAL_STORE_FAILURE)
        } ?: return failure(PlaybackResolutionFailureReason.CREDENTIALS_NOT_FOUND)

        if (credentials.username.isBlank() || credentials.password.isBlank()) {
            return failure(PlaybackResolutionFailureReason.CREDENTIALS_INVALID)
        }

        val baseUrl = validatedServer.normalizedUrl.toHttpUrlOrNull()
            ?: return failure(PlaybackResolutionFailureReason.SOURCE_LOCATOR_INVALID)
        val resolved = baseUrl.newBuilder()
            .addPathSegment("live")
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment("$streamId.ts")
            .build()
            .toString()

        return success(resolved, ResolvedPlaybackOrigin.XTREAM_LIVE)
    }

    private fun sensitiveRef(value: String): SensitiveValueRef? = try {
        SensitiveValueRef(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun success(
        value: String,
        origin: ResolvedPlaybackOrigin,
    ): PlaybackResolutionResult = PlaybackResolutionResult.Success(
        ResolvedPlaybackLocator(value = value, origin = origin),
    )

    private fun failure(reason: PlaybackResolutionFailureReason): PlaybackResolutionResult =
        PlaybackResolutionResult.Failure(reason)
}
