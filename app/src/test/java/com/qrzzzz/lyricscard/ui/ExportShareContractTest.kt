package com.qrzzzz.lyricscard.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.renderer.ExportedImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportShareContractTest {
    @Test
    fun missingOrNotYetValidatedResultReturnsAResourceErrorWithoutDispatching() {
        val project = ProjectTemplates.blank(id = "share-readiness", now = 1L)
        val state = ExportUiState(
            projectId = project.id,
            project = project,
            isLoading = false,
        )

        assertEquals(
            UiText.resource(R.string.export_result_missing_error),
            shareReadinessError(state),
        )
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            assertNull(
                shareReadinessError(
                    state.copy(
                        operation = ExportOperationState.SUCCESS,
                        exported = ExportedImage(File("ready.png"), 10, 10),
                        preview = ExportPreviewUiState(
                            phase = ExportPreviewPhase.READY,
                            bitmap = bitmap,
                        ),
                    ),
                ),
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun shareIntentCarriesStreamClipDataAndReadPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = ExportedImage(File(context.cacheDir, "exports/share.png"), 10, 10)
        val uri = Uri.parse("content://${context.packageName}.files/exports/share.png")

        val intent = buildShareIntent(context, image, uri)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertNotNull(intent.clipData)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun fileProviderAndUnavailableChooserFailuresReturnSanitizedResourceErrors() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outsideRoot = File.createTempFile("outside-share-root-", ".png").apply { deleteOnExit() }
        assertEquals(
            UiText.resource(R.string.export_error_open_share_sheet),
            shareImage(context, ExportedImage(outsideRoot, 10, 10)),
        )

        val exportDirectory = File(context.cacheDir, "exports").apply { mkdirs() }
        val insideRoot = File(exportDirectory, "share.png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        var startAttempted = false
        val noActivityContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                startAttempted = true
                throw ActivityNotFoundException()
            }
        }

        assertEquals(
            UiText.resource(R.string.export_error_open_share_sheet),
            shareImage(noActivityContext, ExportedImage(insideRoot, 10, 10)),
        )
        assertTrue(startAttempted)
    }
}
