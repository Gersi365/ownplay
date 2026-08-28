package app.ownplay.player.ui

import app.ownplay.player.source.SourceError
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceErrorMessageTest {
    @Test
    fun urlValidationErrorsExplainTheActionToTake() {
        assertEquals("Use an HTTP or HTTPS URL.", sourceErrorMessage(SourceError.UnsupportedScheme))
        assertEquals("Enter a URL with a server host.", sourceErrorMessage(SourceError.MissingHost))
        assertEquals(
            "Enter credentials in their fields, not in the URL.",
            sourceErrorMessage(SourceError.EmbeddedCredentialsNotAllowed),
        )
        assertEquals(
            "Remove unsupported URL components.",
            sourceErrorMessage(SourceError.UnexpectedUrlComponent),
        )
    }

    @Test
    fun credentialErrorsDistinguishInputStorageAndAuthentication() {
        assertEquals(
            "Enter a valid username and password.",
            sourceErrorMessage(SourceError.InvalidCredentials),
        )
        assertEquals(
            "Saved credentials are unavailable.",
            sourceErrorMessage(SourceError.CredentialUnavailable),
        )
        assertEquals(
            "Authentication failed. Check your username and password.",
            sourceErrorMessage(SourceError.AuthenticationFailed),
        )
    }
}
