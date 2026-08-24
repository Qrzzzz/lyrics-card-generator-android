package com.qrzzzz.lyricscard

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellResourceContractTest {
    @Test
    fun `manifest wires modern splash adaptive round icon and resizable IME shell`() {
        val manifest = appFile("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        assertTrue(manifest.contains("android:theme=\"@style/Theme.LyricsCard.Starting\""))
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
        assertTrue(manifest.contains("android:screenOrientation=\"fullUser\""))
    }

    @Test
    fun `splash theme has icon background and post theme without fixed light system bars`() {
        val themes = appFile("src/main/res/values/themes.xml").readText()

        assertTrue(themes.contains("parent=\"Theme.SplashScreen\""))
        assertTrue(themes.contains("windowSplashScreenBackground"))
        assertTrue(themes.contains("windowSplashScreenAnimatedIcon"))
        assertTrue(themes.contains("postSplashScreenTheme"))
        assertFalse(themes.contains("windowLightStatusBar"))
        assertFalse(themes.contains("windowLightNavigationBar"))
        assertFalse(appFile("src/main/res/values-v27/themes.xml", required = false).exists())
    }

    @Test
    fun `activity installs splash before super and reapplies stable edge to edge policy`() {
        val activity = appFile("src/main/java/com/qrzzzz/lyricscard/MainActivity.kt").readText()

        assertTrue(activity.indexOf("installSplashScreen()") in 0 until activity.indexOf("super.onCreate"))
        assertTrue(activity.contains("enableEdgeToEdge()"))
        assertTrue(activity.contains("SystemBarStyle.auto"))
        assertTrue(activity.contains("detectDarkMode = { darkTheme }"))
        assertTrue(activity.contains("LIGHT_NAVIGATION_BAR_SCRIM"))
        assertTrue(activity.contains("DARK_NAVIGATION_BAR_SCRIM"))
    }

    @Test
    fun `launcher resources use the canonical repository artwork across every color path`() {
        val foreground = appFile("src/main/res/drawable/ic_launcher_foreground.xml").readText()
        val launcher = appFile("src/main/res/mipmap-nodpi/ic_launcher.png")
        val foregroundArtwork = appFile("src/main/res/mipmap-nodpi/lyric_card_generator_icon.png")
        val canonical = repoFile("assets/branding/lyric-card-generator-icon.png")
        val adaptive = appFile("src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val round = appFile("src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").readText()
        val themed = appFile("src/main/res/mipmap-anydpi-v33/ic_launcher.xml").readText()

        assertTrue(adaptive.contains("<adaptive-icon"))
        assertTrue(adaptive.contains("@color/launcher_background"))
        assertTrue(adaptive.contains("@drawable/ic_launcher_foreground"))
        assertTrue(round.contains("<adaptive-icon"))
        assertFalse(themed.contains("<monochrome"))
        assertTrue(foreground.contains("<bitmap"))
        assertTrue(foreground.contains("android:gravity=\"fill\""))
        assertTrue(foreground.contains("android:src=\"@mipmap/lyric_card_generator_icon\""))
        assertTrue(canonical.readBytes().contentEquals(launcher.readBytes()))
        assertTrue(canonical.readBytes().contentEquals(foregroundArtwork.readBytes()))
        assertEquals(
            "b3e613afa7695f7fe9b2b72ab8681647d37dc3a210292bce48b80d96b9daaf58",
            launcher.inputStream().use { stream ->
                MessageDigest.getInstance("SHA-256")
                    .digest(stream.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            },
        )
    }

    @Test
    fun `brand name is shared by every build variant and the home shell`() {
        val strings = appFile("src/main/res/values/strings.xml").readText()
        val build = appFile("build.gradle.kts").readText()

        assertTrue(strings.contains("<string name=\"app_name\" translatable=\"false\">Lyric Card Generator</string>"))
        assertTrue(strings.contains("<string name=\"home_title\" translatable=\"false\">Lyric Card Generator</string>"))
        assertFalse(build.contains("resValue(\"string\", \"app_name\""))
    }

    @Test
    fun `splash dependency is a pinned stable release`() {
        val catalog = repoFile("gradle/libs.versions.toml").readText()
        val build = appFile("build.gradle.kts").readText()

        assertTrue(catalog.contains("core-splashscreen = \"1.2.0\""))
        assertFalse(Regex("core-splashscreen\\s*=\\s*\"[^\"]*(alpha|beta|rc)").containsMatchIn(catalog))
        assertTrue(build.contains("implementation(libs.androidx.core.splashscreen)"))
    }

    @Test
    fun `compact landscape and IME shell paths remain explicit`() {
        val editor = appFile("src/main/java/com/qrzzzz/lyricscard/ui/EditorScreen.kt").readText()
        val editorNavigation = appFile("src/main/java/com/qrzzzz/lyricscard/ui/EditorNavigation.kt").readText()
        val export = appFile("src/main/java/com/qrzzzz/lyricscard/ui/ExportScreen.kt").readText()
        val imeInsets = appFile("src/main/java/com/qrzzzz/lyricscard/ui/LyricsImeInsets.kt").readText()
        val adaptive = appFile("src/main/java/com/qrzzzz/lyricscard/ui/WindowAdaptive.kt").readText()

        assertTrue(adaptive.contains("calculateWindowSizeClass"))
        assertTrue(adaptive.contains("WindowWidthSizeClass.Medium"))
        assertTrue(adaptive.contains("WindowWidthSizeClass.Expanded"))
        assertTrue(editor.contains("CompactEditorBottomSheet"))
        assertTrue(editor.contains("LyricsWindowWidth.MEDIUM"))
        assertTrue(editor.contains("LyricsWindowWidth.EXPANDED"))
        assertTrue(editor.contains("rememberLyricsImeInsets()"))
        assertTrue(editor.contains("imeInsets = imeInsets"))
        assertTrue(editorNavigation.contains("windowInsetsPadding(effectiveImeWindowInsets)"))
        assertTrue(imeInsets.contains("WindowInsets.ime"))
        assertTrue(imeInsets.contains("WindowInsetsCompat.Type.ime()"))
        assertTrue(imeInsets.contains("Build.VERSION.SDK_INT == Build.VERSION_CODES.O"))
        assertTrue(imeInsets.contains("composeBottomPx == 0"))
        assertTrue(export.contains("LyricsWindowWidth.MEDIUM"))
        assertTrue(export.contains("LyricsWindowWidth.EXPANDED"))
        assertFalse(editor.contains("LyricsCardLayout.wideBreakpoint"))
        assertFalse(export.contains("LyricsCardLayout.propertiesPaneWidth"))
    }

    private fun appFile(relative: String, required: Boolean = true): File {
        val root = File(checkNotNull(System.getProperty("user.dir")))
        val candidates = listOf(root.resolve("app/$relative"), root.resolve(relative))
        val file = candidates.firstOrNull(File::exists) ?: candidates.first()
        if (required) check(file.exists()) { "Missing app file: $relative (user.dir=$root)" }
        return file
    }

    private fun repoFile(relative: String): File {
        val root = File(checkNotNull(System.getProperty("user.dir")))
        return listOfNotNull(root.resolve(relative), root.parentFile?.resolve(relative))
            .firstOrNull(File::exists)
            ?: error("Missing repo file: $relative (user.dir=$root)")
    }
}
