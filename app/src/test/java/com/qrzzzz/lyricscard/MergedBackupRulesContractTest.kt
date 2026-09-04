package com.qrzzzz.lyricscard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MergedBackupRulesContractTest {
    @Test
    fun `merged variant manifest retains both backup rule bindings`() {
        assertManifestPolicy(mergedApplication())
    }

    @Test
    fun `compiled variant legacy rules exclude every backup domain`() {
        val root = compiledXml(R.xml.backup_rules)

        assertEquals("full-backup-content", root.tagName)
        assertDenyAll(root)
    }

    @Test
    fun `compiled variant Android 12 rules deny cloud backup and device transfer`() {
        assertExtractionPolicy(compiledXml(R.xml.data_extraction_rules))
    }

    @Test
    fun `manifest contract rejects enabled backup and missing or replaced rule bindings`() {
        val application = mergedApplication()
        listOf(
            "allowBackup" to "true",
            "fullBackupContent" to "@xml/other_rules",
            "dataExtractionRules" to "@xml/other_rules",
        ).forEach { (attribute, replacement) ->
            val changed = application.cloneNode(true) as Element
            changed.setAttributeNS(ANDROID_NAMESPACE, "android:$attribute", replacement)
            assertThrows(AssertionError::class.java) { assertManifestPolicy(changed) }

            changed.removeAttributeNS(ANDROID_NAMESPACE, attribute)
            assertThrows(AssertionError::class.java) { assertManifestPolicy(changed) }
        }
    }

    @Test
    fun `extraction contract rejects omitted transfer sections and missing domain exclusions`() {
        val rules = compiledXml(R.xml.data_extraction_rules)
        listOf("cloud-backup", "device-transfer").forEach { sectionName ->
            val missingSection = rules.cloneNode(true) as Element
            missingSection.removeChild(missingSection.singleChild(sectionName))
            assertThrows(AssertionError::class.java) { assertExtractionPolicy(missingSection) }

            val missingDomain = rules.cloneNode(true) as Element
            val section = missingDomain.singleChild(sectionName)
            section.removeChild(section.children().first())
            assertThrows(AssertionError::class.java) { assertExtractionPolicy(missingDomain) }
        }
    }

    @Test
    fun `deny all contract rejects include rules in legacy and Android 12 resources`() {
        val extractionRules = compiledXml(R.xml.data_extraction_rules)
        listOf(
            compiledXml(R.xml.backup_rules),
            extractionRules.singleChild("cloud-backup"),
            extractionRules.singleChild("device-transfer"),
        ).forEach { section ->
            section.appendChild(section.ownerDocument.createElement("include").apply {
                setAttribute("domain", "file")
                setAttribute("path", ".")
            })
            assertThrows(AssertionError::class.java) { assertDenyAll(section) }
        }
    }

    private fun assertManifestPolicy(application: Element) {
        assertEquals("false", application.getAttributeNS(ANDROID_NAMESPACE, "allowBackup"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(ANDROID_NAMESPACE, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(ANDROID_NAMESPACE, "dataExtractionRules"))
    }

    private fun assertExtractionPolicy(root: Element) {
        assertEquals("data-extraction-rules", root.tagName)
        assertDenyAll(root.singleChild("cloud-backup"))
        assertDenyAll(root.singleChild("device-transfer"))
    }

    private fun assertDenyAll(section: Element) {
        val children = section.children()
        assertFalse("Deny-all policy must not contain include rules", children.any { it.tagName == "include" })
        val exclusions = children.filter { it.tagName == "exclude" }
        assertEquals(BACKUP_DOMAINS.size, exclusions.size)
        assertEquals(
            BACKUP_DOMAINS.map { it to "." }.toSet(),
            exclusions.map { it.getAttribute("domain") to it.getAttribute("path") }.toSet(),
        )
    }

    private fun mergedApplication(): Element {
        // Use the app variant artifact, not src/main or the separate unit-test host manifest.
        val manifest = File(checkNotNull(System.getProperty("lyricscard.mergedManifest")) {
            "Missing AGP merged app manifest input"
        })
        check(manifest.isFile) { "Missing merged variant manifest: $manifest" }
        return documentFactory().newDocumentBuilder().parse(manifest)
            .documentElement.singleChild("application")
    }

    private fun compiledXml(resourceId: Int): Element {
        val document = documentFactory().newDocumentBuilder().newDocument()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.resources.getXml(resourceId).use { parser ->
            var parent: Element? = null
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        val element = document.createElement(parser.name)
                        repeat(parser.attributeCount) { index ->
                            element.setAttribute(parser.getAttributeName(index), parser.getAttributeValue(index))
                        }
                        (parent ?: document).appendChild(element)
                        parent = element
                    }
                    XmlPullParser.END_TAG -> parent = parent?.parentNode as? Element
                }
            }
        }
        return checkNotNull(document.documentElement) { "Compiled XML resource is empty: $resourceId" }
    }

    private fun documentFactory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }

    private fun Element.singleChild(tagName: String): Element {
        val matches = children().filter { it.tagName == tagName }
        assertEquals("Expected exactly one <$tagName> section", 1, matches.size)
        return matches.single()
    }

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        val BACKUP_DOMAINS = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
