package app.ownplay.player.ui

import app.ownplay.player.source.SourceError

internal fun sourceErrorMessage(error: SourceError): String = when (error) {
    SourceError.EmptyValue -> "A required value is empty."
    SourceError.InvalidUrl -> "Invalid source URL."
    SourceError.UnsupportedScheme -> "Use an HTTP or HTTPS URL."
    SourceError.MissingHost -> "Enter a URL with a server host."
    SourceError.EmbeddedCredentialsNotAllowed ->
        "Enter credentials in their fields, not in the URL."
    SourceError.UnexpectedUrlComponent -> "Remove unsupported URL components."
    SourceError.UnsupportedLocalUri -> "Unsupported local file URI."
    SourceError.InvalidCredentials -> "Enter a valid username and password."
    SourceError.CredentialUnavailable -> "Saved credentials are unavailable."
    SourceError.AuthenticationFailed -> "Authentication failed. Check your username and password."
    SourceError.CleartextTransportRequiresOptIn -> "Enable HTTP for this provider."
    SourceError.SecureConnectionFailed -> "Secure connection failed."
    SourceError.NetworkUnavailable -> "Network unavailable."
    SourceError.Timeout -> "Provider timed out."
    SourceError.SourceReadFailed -> "Could not read the source."
    is SourceError.HttpFailure -> "Provider returned HTTP ${error.statusCode}."
    SourceError.MalformedResponse -> "Provider returned an unsupported response."
    SourceError.MalformedPlaylist -> "Playlist data is malformed."
    SourceError.Unknown -> "Could not connect to this source."
}
