package app.ownplay.player.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildTargetSeparationRegressionTest {
    @Test
    fun `Gradle defines Mobile and TV flavors with one shared application id`() {
        val gradle = source("build.gradle.kts")

        assertTrue(gradle.contains("flavorDimensions += \"device\""))
        assertTrue(gradle.contains("create(\"mobile\")"))
        assertTrue(gradle.contains("create(\"tv\")"))
        assertTrue(gradle.contains("applicationId = \"app.ownplay.player\""))
        assertTrue(gradle.contains("versionCode = 6"))
        assertFalse(gradle.contains("applicationIdSuffix"))
    }

    @Test
    fun `launcher discovery is separated by target manifests`() {
        val shared = source("src/main/AndroidManifest.xml")
        val mobile = source("src/mobile/AndroidManifest.xml")
        val tv = source("src/tv/AndroidManifest.xml")

        assertFalse(shared.contains("android.intent.category.LAUNCHER"))
        assertFalse(shared.contains("android.intent.category.LEANBACK_LAUNCHER"))

        assertTrue(mobile.contains("android.hardware.touchscreen"))
        assertTrue(mobile.contains("android:required=\"true\""))
        assertTrue(mobile.contains("android.intent.category.LAUNCHER"))
        assertFalse(mobile.contains("android.intent.category.LEANBACK_LAUNCHER"))

        assertTrue(tv.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue(tv.contains("android.intent.category.LAUNCHER"))
        assertTrue(tv.contains("android:supportsPictureInPicture=\"false\""))
        assertTrue(tv.contains("android:screenOrientation=\"landscape\""))
    }

    @Test
    fun `shell and input target are selected at compile time`() {
        val root = source("src/main/java/app/ownplay/player/ui/OwnPlayRoot.kt")
        val activity = source("src/main/java/app/ownplay/player/MainActivity.kt")
        val mobileTarget = source("src/mobile/java/app/ownplay/player/target/OwnPlayBuildTarget.kt")
        val tvTarget = source("src/tv/java/app/ownplay/player/target/OwnPlayBuildTarget.kt")
        val mobileRoot = source("src/mobile/java/app/ownplay/player/ui/PlatformOwnPlayRoot.kt")
        val tvRoot = source("src/tv/java/app/ownplay/player/ui/PlatformOwnPlayRoot.kt")

        assertFalse(root.contains("UI_MODE_TYPE_TELEVISION"))
        assertTrue(root.contains("PlatformOwnPlayRoot("))
        assertTrue(activity.contains("OwnPlayTargetBehavior"))
        assertFalse(activity.contains("TvRemoteActionGuard"))
        assertFalse(activity.contains("TvPlaybackLifecyclePolicy"))

        assertTrue(mobileTarget.contains("usesDpad: Boolean = false"))
        assertTrue(mobileTarget.contains("supportsTouchInput: Boolean = true"))
        assertTrue(mobileTarget.contains("AppDeviceProfile.SMARTPHONE"))
        assertTrue(mobileTarget.contains("AppDeviceProfile.TABLET"))
        assertTrue(mobileRoot.contains("OwnPlayApp("))
        assertFalse(mobileRoot.contains("OwnPlayTvApp("))

        assertTrue(tvTarget.contains("usesDpad: Boolean = true"))
        assertTrue(tvTarget.contains("supportsTouchInput: Boolean = false"))
        assertTrue(tvTarget.contains("AppDeviceProfile.ANDROID_TV"))
        assertTrue(tvRoot.contains("OwnPlayTvApp("))
        assertFalse(tvRoot.contains("OwnPlayApp("))
    }

    @Test
    fun `Mobile onboarding cannot select a TV profile`() {
        val setup = source("src/main/java/app/ownplay/player/ui/OrientationSetupScreen.kt")
        val settings = source("src/main/java/app/ownplay/player/ui/SettingsInterface.kt")

        assertTrue(setup.contains("OwnPlay Mobile supports Smartphone and Tablet layouts"))
        assertFalse(setup.contains("title = \"Android TV\""))
        assertFalse(setup.contains("title = \"TV Box\""))
        assertFalse(settings.contains("OrientationButton(\n            label = \"Android TV\""))
        assertFalse(settings.contains("OrientationButton(\n            label = \"TV Box\""))
        assertTrue(settings.contains("Android TV / TV Box"))
    }

    @Test
    fun `CI validates both target variants explicitly`() {
        val ci = repoSource(".github/workflows/android-ci.yml")
        val noApk = repoSource(".github/workflows/android-validation-no-apk.yml")

        assertTrue(ci.contains(":app:testMobileDebugUnitTest"))
        assertTrue(ci.contains(":app:assembleMobileDebug"))
        assertTrue(ci.contains(":app:testTvDebugUnitTest"))
        assertTrue(ci.contains(":app:assembleTvDebug"))
        assertTrue(ci.contains("ownplay-mobile-debug-apk"))
        assertTrue(ci.contains("ownplay-tv-debug-apk"))

        assertTrue(noApk.contains(":app:kspMobileDebugKotlin"))
        assertTrue(noApk.contains(":app:kspTvDebugKotlin"))
        assertFalse(noApk.contains("assembleMobileDebug"))
        assertFalse(noApk.contains("assembleTvDebug"))
    }

    private fun source(relativeToApp: String): String {
        val candidates = listOf(
            File(relativeToApp),
            File("app/$relativeToApp"),
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Source file not found: $relativeToApp")
        return file.readText()
    }

    private fun repoSource(path: String): String {
        val candidates = listOf(
            File(path),
            File("../$path"),
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("Repository file not found: $path")
        return file.readText()
    }
}
