package com.qrzzzz.lyricscard.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Observes real activity launches without replacing their results or blocking the platform UI. */
internal class PlatformExportUiDriver(
    private val context: Context,
    private val fixtureName: String,
    private val advanceAppFrames: () -> Unit,
) : Closeable {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation
    private val previousServiceFlags = automation.serviceInfo.flags
    private val createDocument = AtomicReference<Intent?>()
    private val chooser = AtomicReference<Intent?>()
    private var sharedUri: Uri? = null
    private val destination = "/sdcard/Download/$fixtureName"
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            when (intent.action) {
                Intent.ACTION_CREATE_DOCUMENT -> createDocument.set(Intent(intent))
                Intent.ACTION_CHOOSER -> chooser.set(Intent(intent))
            }
            return null
        }
    }

    init {
        require(fixtureName.matches(Regex("lcg-ui-validation-[a-f0-9-]{36}\\.png")))
        assertFalse("unique validation destination already exists", shell("test -e $destination && echo exists").contains("exists"))
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        instrumentation.addMonitor(monitor)
    }

    fun saveUsingDocumentsUi() {
        await("UI Save did not launch ACTION_CREATE_DOCUMENT", pumpAppFrames = true) { createDocument.get() != null }
        val intent = checkNotNull(createDocument.get())
        assertEquals("image/png", intent.type)
        assertEquals(fixtureName, intent.getStringExtra(Intent.EXTRA_TITLE))
        Log.i("LCG_RELEASE", "platform-ui createDocumentObserved=true mime=image/png uniqueFixture=true")
        await("DocumentsUI did not become visible") { foregroundIsDocumentsUi() }

        // Select the platform's local Downloads root, rather than relying on the user's last folder.
        val rootsButton = awaitNode("DocumentsUI roots button") {
            it.contentDescription?.toString() in ROOTS_LABELS || it.viewIdResourceName == "android:id/home"
        }
        click(rootsButton)
        val downloads = awaitNode("DocumentsUI Downloads root") {
            it.text?.toString() in DOWNLOADS_LABELS && it.viewIdResourceName == "android:id/title"
        }
        click(downloads)
        val name = awaitNode("DocumentsUI filename field") { it.className?.toString() == "android.widget.EditText" }
        if (name.text?.toString() != fixtureName) {
            assertTrue("cannot set unique document filename", name.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fixtureName) },
            ))
        }
        val save = awaitNode("DocumentsUI Save button") {
            val id = it.viewIdResourceName.orEmpty().substringAfterLast('/')
            id == "action_menu_save" || (id == "button1" && it.text?.toString()?.lowercase() in SAVE_LABELS)
        }
        click(save)
        await("DocumentsUI did not return to the app") { foregroundIsApp() }
        Log.i("LCG_RELEASE", "platform-ui documentsSaveClicked=true returnedToApp=true")
    }

    fun readSavedPng(): ByteArray {
        // The bytes are read independently from the actual document created by the system picker.
        // No test helper writes a destination or calls ExportViewModel.saveTo/exportFiles.copyTo.
        val bytes = shellBytes("cat $destination")
        assertTrue("system-created validation document is empty or missing", bytes.size > 8)
        return bytes
    }

    @Suppress("DEPRECATION")
    fun readSharedPngFromVisibleChooser(): ByteArray {
        await("UI Share did not launch ACTION_CHOOSER", pumpAppFrames = true) { chooser.get() != null }
        val send = checkNotNull(chooser.get()?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertTrue("actual share intent lacks read permission", send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.files", uri.authority)
        assertEquals("actual share clip URI differs", uri, send.clipData?.getItemAt(0)?.uri)
        sharedUri = uri
        await("actual platform share chooser did not become visible") { foregroundIsChooser() }
        Log.i("LCG_RELEASE", "platform-ui shareObserved=true actualStreamAndClipMatch=true readGrant=true chooserVisible=true")
        return checkNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
    }

    fun dismissChooserWithoutSending() {
        assertTrue("share chooser is no longer visible", foregroundIsChooser())
        shell("input keyevent 4")
        await("share chooser did not return to the app") { foregroundIsApp() }
    }

    override fun close() {
        instrumentation.removeMonitor(monitor)
        try {
            try {
                repeat(3) {
                    if (foregroundIsChooser() || foregroundIsDocumentsUi()) {
                        shell("input keyevent 4")
                        SystemClock.sleep(300)
                    }
                }
                await("external export UI did not dismiss") { foregroundIsApp() }
            } finally {
                // Only this invocation's UUID-named external fixture and observed app-owned export are removed.
                shell("rm -f $destination")
                assertFalse("unique validation document cleanup failed", shell("test -e $destination && echo exists").contains("exists"))
                sharedUri?.let { uri ->
                    assertEquals("app-owned UI export cleanup failed", 1, context.contentResolver.delete(uri, null, null))
                }
            }
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = previousServiceFlags }
        }
    }

    private fun foregroundIsApp(): Boolean = resumedActivities().any { it.contains("${context.packageName}/") }
    private fun foregroundIsDocumentsUi(): Boolean = resumedActivities().any { it.contains("documentsui", ignoreCase = true) }
    private fun foregroundIsChooser(): Boolean = resumedActivities().any {
        it.contains("Chooser", ignoreCase = true) || it.contains("Resolver", ignoreCase = true)
    }

    private fun resumedActivities(): List<String> = shell("dumpsys activity activities").lineSequence()
        .filter { it.contains("mResumedActivity") || it.contains("topResumedActivity") }.toList()

    private fun awaitNode(description: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        await("missing $description") {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let(queue::add)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isVisibleToUser && predicate(node)) {
                    found = node
                    break
                }
                repeat(node.childCount) { node.getChild(it)?.let(queue::add) }
            }
            found != null
        }
        return checkNotNull(found)
    }

    private fun click(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        repeat(6) {
            val current = target ?: return@repeat
            if (current.isClickable && current.isEnabled && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            target = current.parent
        }
        throw AssertionError("platform UI node could not be clicked")
    }

    private fun await(message: String, pumpAppFrames: Boolean = false, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (pumpAppFrames) advanceAppFrames()
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError(message)
    }

    private fun shell(command: String): String = shellBytes(command).toString(Charsets.UTF_8)
    private fun shellBytes(command: String): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command),
    ).use { it.readBytes() }

    private companion object {
        val ROOTS_LABELS = setOf("Show roots", "Open navigation drawer", "显示根目录", "显示根路径", "開啟導覽匣")
        val DOWNLOADS_LABELS = setOf("Downloads", "Download", "下载", "下載")
        val SAVE_LABELS = setOf("save", "保存", "儲存")
    }
}
