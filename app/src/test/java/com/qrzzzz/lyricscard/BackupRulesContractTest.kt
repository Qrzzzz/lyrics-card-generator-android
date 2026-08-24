package com.qrzzzz.lyricscard

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupRulesContractTest {
    @Test
    fun `manifest binds legacy and Android 12 backup rules`() {
        val application = parse(appFile("src/main/AndroidManifest.xml"))
            .documentElement
            .getElementsByTagName("application")
            .item(0) as Element

        assertEquals("false", application.androidAttribute("allowBackup"))
        assertEquals("@xml/backup_rules", application.androidAttribute("fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.androidAttribute("dataExtractionRules"))
    }

    @Test
    fun `legacy rules exclude every backup domain`() {
        val root = parse(appFile("src/main/res/xml/backup_rules.xml")).documentElement

        assertEquals("full-backup-content", root.tagName)
        assertDenyAll(root)
    }

    @Test
    fun `Android 12 rules deny both cloud backup and device transfer`() {
        val root = parse(appFile("src/main/res/xml/data_extraction_rules.xml")).documentElement

        assertEquals("data-extraction-rules", root.tagName)
        assertDenyAll(root.singleChild("cloud-backup"))
        assertDenyAll(root.singleChild("device-transfer"))
    }

    private fun assertDenyAll(element: Element) {
        assertFalse("Deny-all policy must not contain include rules", element.hasChild("include"))
        val exclusions = element.children("exclude")
            .map { child -> child.getAttribute("domain") to child.getAttribute("path") }
            .toSet()

        assertEquals(BACKUP_DOMAINS.map { domain -> domain to "." }.toSet(), exclusions)
        assertEquals(BACKUP_DOMAINS.size, element.children("exclude").size)
    }

    private fun Element.singleChild(tagName: String): Element {
        val matches = children(tagName)
        assertEquals("Expected exactly one <$tagName> section", 1, matches.size)
        return matches.single()
    }

    private fun Element.hasChild(tagName: String): Boolean = children(tagName).isNotEmpty()

    private fun Element.children(tagName: String): List<Element> =
        (0 until childNodes.length)
            .map(childNodes::item)
            .filterIsInstance<Element>()
            .filter { child -> child.tagName == tagName }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

    private fun appFile(relative: String): File {
        val root = File(checkNotNull(System.getProperty("user.dir")))
        val candidates = listOf(root.resolve("app/$relative"), root.resolve(relative))
        return candidates.firstOrNull(File::isFile)
            ?: error("Missing app file: $relative (user.dir=$root)")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        val BACKUP_DOMAINS = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
    }
}
