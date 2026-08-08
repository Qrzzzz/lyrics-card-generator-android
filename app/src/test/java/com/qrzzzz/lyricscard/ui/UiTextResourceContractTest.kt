package com.qrzzzz.lyricscard.ui

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.R
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UiTextResourceContractTest {
    @Test
    fun `typed text resolves nested resources in LTR Chinese at normal and large font scales`() {
        listOf(1f, 2f).forEach { fontScale ->
            val configuration = Configuration().apply {
                setLocale(Locale.SIMPLIFIED_CHINESE)
                setLayoutDirection(Locale.SIMPLIFIED_CHINESE)
                this.fontScale = fontScale
            }
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                .createConfigurationContext(configuration)
            val text = UiText.resource(
                R.string.editor_netease_import_success,
                UiText.joined(
                    R.string.list_separator,
                    listOf(
                        UiText.resource(R.string.editor_import_part_song),
                        UiText.resource(R.string.editor_import_part_lyrics),
                    ),
                ),
            )

            assertEquals("已从网易云导入歌曲信息、歌词", text.resolve(context))
            assertEquals(Configuration.SCREENLAYOUT_LAYOUTDIR_LTR, configuration.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK)
            assertEquals(fontScale, context.resources.configuration.fontScale)
        }
    }

    @Test
    fun `screen copy is resource backed and enum names are not displayed`() {
        screenFiles().forEach { file ->
            val source = file.readText()
            assertFalse("Hardcoded Han UI text in ${file.name}", Regex("[\\p{IsHan}]").containsMatchIn(source))
            assertFalse("Hardcoded Text literal in ${file.name}", Regex("Text\\(\\s*\"").containsMatchIn(source))
            assertFalse("Hardcoded content description in ${file.name}", Regex("contentDescription\\s*=\\s*\"").containsMatchIn(source))
            assertFalse("enum.name display in ${file.name}", source.contains(".name.lowercase"))
        }
    }

    @Test
    fun `state owners remain free of Android context and composable resource APIs`() {
        viewModelFiles().forEach { file ->
            val source = file.readText()
            assertFalse("Context leaked into ${file.name}", source.contains("android.content.Context"))
            assertFalse("stringResource leaked into ${file.name}", source.contains("stringResource"))
            assertFalse("LocalContext leaked into ${file.name}", source.contains("LocalContext"))
            assertTrue("UiText missing from ${file.name}", source.contains("UiText"))
        }
    }

    private fun screenFiles(): List<File> = sourceRoot().let { root ->
        listOf(
            root.resolve("ui/HomeScreen.kt"),
            root.resolve("ui/EditorScreen.kt"),
            root.resolve("ui/EditorChooseSong.kt"),
            root.resolve("ui/EditorExportPanel.kt"),
            root.resolve("ui/EditorLayoutPanel.kt"),
            root.resolve("ui/EditorLyricsPanel.kt"),
            root.resolve("ui/EditorNavigation.kt"),
            root.resolve("ui/EditorSettings.kt"),
            root.resolve("ui/EditorTypographyPanel.kt"),
            root.resolve("ui/EditorVisualPanel.kt"),
            root.resolve("ui/ExportScreen.kt"),
            root.resolve("ui/SettingsScreen.kt"),
            root.resolve("renderer/RendererPreview.kt"),
        )
    }.onEach { check(it.isFile) { "Missing screen source: $it" } }

    private fun viewModelFiles(): List<File> = sourceRoot().let { root ->
        listOf(
            root.resolve("ui/HomeViewModel.kt"),
            root.resolve("ui/EditorViewModel.kt"),
            root.resolve("ui/ExportViewModel.kt"),
            root.resolve("ui/SettingsViewModel.kt"),
        )
    }.onEach { check(it.isFile) { "Missing state owner source: $it" } }

    private fun sourceRoot(): File {
        val root = File(checkNotNull(System.getProperty("user.dir")))
        return listOf(root.resolve("app/src/main/java/com/qrzzzz/lyricscard"), root.resolve("src/main/java/com/qrzzzz/lyricscard"))
            .firstOrNull(File::isDirectory)
            ?: error("Missing application source root (user.dir=$root)")
    }
}
