package app.ownplay.player.playback

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSensitiveLoggingTest {
    @Test
    fun productionSourceDoesNotUseUnstructuredLoggingApis() {
        val sourceRoot = listOf(
            Path.of("src", "main", "java", "app", "ownplay", "player"),
            Path.of("app", "src", "main", "java", "app", "ownplay", "player"),
        ).firstOrNull(Files::isDirectory)
        requireNotNull(sourceRoot) { "OwnPlay production source root was not found" }

        val forbidden = listOf(
            "android.util.Log",
            "Timber.",
            "println(",
            "printStackTrace(",
            "System.out",
            "System.err",
        )

        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths.filter { path ->
                Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt")
            }.forEach { path ->
                val text = Files.readString(path)
                forbidden.forEach { token ->
                    if (token in text) {
                        violations += "${path.toString().replace('\\', '/')}: $token"
                    }
                }
            }
        }

        assertTrue(
            "Production logging surface must remain structured/redacted: $violations",
            violations.isEmpty(),
        )
    }
}
