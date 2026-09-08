package app.ownplay.player.ui.tv

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFreshInstallIdentityContractTest {
    @Test
    fun `tv flavor uses neutral standalone identity`() {
        val buildFile = File("build.gradle.kts").readText()
        val tvBlock = Regex(
            """create\("tv"\)\s*\{([\s\S]*?)\n        \}""",
        ).find(buildFile)?.groupValues?.get(1)
            ?: error("TV product flavor not found")

        assertTrue(
            "TV must keep the established application id.",
            "applicationId = \"app.ownplay.tv\"" in tvBlock,
        )
        assertTrue(
            "TV must expose a neutral version name with no historical rollout wording.",
            "versionName = \"1.0.13\"" in tvBlock,
        )
        assertFalse(
            "TV identity must not carry update lineage wording.",
            "update" in tvBlock.lowercase(),
        )
        assertFalse(
            "TV identity must not carry modernization lineage wording.",
            "modernization" in tvBlock.lowercase(),
        )
    }
}
