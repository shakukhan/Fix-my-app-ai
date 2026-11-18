package com.example

import com.example.data.AiRepairRepository
import com.example.engine.PreviewBundleBuilder
import com.example.engine.StaticWebsiteInspector
import com.example.engine.WebsiteAutoRepairer
import com.example.engine.ZipProjectImporter
import com.example.model.FileType
import com.example.model.ProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class WebsiteInspectorRepairTest {

    @Test
    fun `sweet bakery demo detects 12 issues and preview renders before and after repair`() {
        val sample = ZipProjectImporter.createSampleBrokenProjectFiles()
        assertEquals("Sweet Bakery Demo", sample.suggestedName)

        val projectFiles = sample.files.map {
            ProjectFile(
                id = it.relativePath,
                userId = "test_user",
                projectId = "test_proj",
                relativePath = it.relativePath,
                fileType = it.fileType.name,
                sizeBytes = it.sizeBytes,
                originalContent = it.textContent
            )
        }

        // 1. Preview BEFORE error detection works cleanly with original files
        val previewBeforeInspection = PreviewBundleBuilder.build(projectFiles, useRepaired = false)
        assertNotNull(previewBeforeInspection)
        assertEquals("index.html", previewBeforeInspection!!.entryPath)
        assertTrue(previewBeforeInspection.entryHtmlWithMonitor.contains("Welcome to Sweet Bakery"))
        assertNull(
            "Case-mismatched Style.css should fail exact lookup before repair",
            PreviewBundleBuilder.resolveAssetForUrl(previewBeforeInspection, "${PreviewBundleBuilder.PREVIEW_ORIGIN}Style.css")
        )
        assertNotNull(
            "app.js must resolve cleanly in Preview",
            PreviewBundleBuilder.resolveAssetForUrl(previewBeforeInspection, "${PreviewBundleBuilder.PREVIEW_ORIGIN}app.js")
        )

        // 2. Run error detection -> confirms 12 errors while preserving preview & code
        val before = StaticWebsiteInspector.inspect(projectFiles, useRepaired = false, "test_user", "test_proj")
        assertEquals("Sweet Bakery Demo should detect exact 12 issues", 12, before.issues.size)
        assertTrue("Expected critical issues before repair", before.criticalCount > 0)
        assertTrue(before.issues.any { it.code == "HTML_MISSING_VIEWPORT" })
        assertTrue(before.issues.any { it.code == "PATH_CASE_MISMATCH" })
        assertTrue(before.issues.any { it.code == "PATH_WINDOWS_BACKSLASH" })
        assertTrue(before.issues.any { it.code == "CSS_UNCLOSED_BRACE" })
        assertTrue(before.issues.any { it.code == "JS_GETELEMENTBYID_TYPO" })

        val previewAfterInspection = PreviewBundleBuilder.build(projectFiles, useRepaired = false)
        assertNotNull(previewAfterInspection)
        assertEquals(previewBeforeInspection.contentSignature, previewAfterInspection!!.contentSignature)

        // 3. Auto Fix / Repair All validates fixes and repairs preview assets
        val outcome = WebsiteAutoRepairer.repairAndValidate(projectFiles, "test_user", "test_proj")
        assertTrue("Expected resolved issues after repair validation", outcome.resolvedIssues.isNotEmpty())
        assertTrue(outcome.resolvedIssues.all { it.fixed })

        // Verify original content remained untouched
        val repairedHtmlFile = outcome.updatedFiles.first { it.relativePath == "index.html" }
        assertEquals(projectFiles.first { it.relativePath == "index.html" }.originalContent, repairedHtmlFile.originalContent)
        assertTrue(repairedHtmlFile.activeContent().contains("viewport"))
        assertTrue(repairedHtmlFile.activeContent().contains("href=\"style.css\""))
        assertTrue(repairedHtmlFile.activeContent().contains("src=\"images/bakery-logo.svg\""))

        // Verify afterReport no longer has repaired issues
        assertTrue(outcome.afterReport.issues.none { it.code == "HTML_MISSING_VIEWPORT" })
        assertTrue(outcome.afterReport.issues.none { it.code == "CSS_UNCLOSED_BRACE" })
        assertTrue(outcome.afterReport.issues.none { it.code == "JS_GETELEMENTBYID_TYPO" })
        assertTrue(outcome.afterReport.issues.none { it.code == "PATH_CASE_MISMATCH" })
        assertTrue(outcome.afterReport.issues.none { it.code == "PATH_WINDOWS_BACKSLASH" })

        // 4. Preview AFTER repair resolves fixed stylesheet, JS, and SVG logo cleanly
        val previewAfterRepair = PreviewBundleBuilder.build(outcome.updatedFiles, useRepaired = true)
        assertNotNull(previewAfterRepair)
        assertTrue(previewAfterRepair!!.hasRepairedFiles)
        assertTrue(previewAfterRepair.entryHtmlWithMonitor.contains("href=\"style.css\""))
        val fixedCss = PreviewBundleBuilder.resolveAssetForUrl(previewAfterRepair, "${PreviewBundleBuilder.PREVIEW_ORIGIN}style.css")
        val fixedJs = PreviewBundleBuilder.resolveAssetForUrl(previewAfterRepair, "${PreviewBundleBuilder.PREVIEW_ORIGIN}app.js")
        val fixedSvg = PreviewBundleBuilder.resolveAssetForUrl(previewAfterRepair, "${PreviewBundleBuilder.PREVIEW_ORIGIN}images/bakery-logo.svg")
        assertNotNull(fixedCss)
        assertNotNull(fixedJs)
        assertNotNull(fixedSvg)
        assertFalse(fixedJs!!.content.contains("getElementByID"))
        assertTrue(fixedJs.content.contains("getElementById"))
        assertTrue(fixedCss!!.content.contains("FixMyApp Responsive Guard"))
    }

    @Test
    fun `preview serves uploaded binary assets and repair fixes inline js console typos`() {
        val files = listOf(
            ProjectFile(
                id = "h1",
                userId = "u1",
                projectId = "p1",
                relativePath = "index.html",
                fileType = com.example.model.FileType.HTML.name,
                sizeBytes = 200L,
                originalContent = """
                    <html><head><link rel="stylesheet" href="./style.css"></head>
                    <body><img src="logo.png" alt="logo">
                    <script>document.getElementByID('a'); document.querySelectorall('.b'); btn.addEventListner('click', fn);</script>
                    </body></html>
                """.trimIndent()
            ),
            ProjectFile(
                id = "c1",
                userId = "u1",
                projectId = "p1",
                relativePath = "style.css",
                fileType = com.example.model.FileType.CSS.name,
                sizeBytes = 20L,
                originalContent = "body { margin: 0; }"
            )
        )
        val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val outcome = WebsiteAutoRepairer.repairAndValidate(files, "u1", "p1")
        val repairedHtml = outcome.updatedFiles.first { it.relativePath == "index.html" }.activeContent()
        assertTrue(repairedHtml.contains("document.getElementById('a')"))
        assertTrue(repairedHtml.contains("document.querySelectorAll('.b')"))
        assertTrue(repairedHtml.contains(".addEventListener('click'"))

        val prepared = PreviewBundleBuilder.build(
            files = outcome.updatedFiles,
            useRepaired = true,
            binaryAssets = mapOf("logo.png" to fakePng)
        )
        assertNotNull(prepared)
        val pngAsset = PreviewBundleBuilder.resolveAssetForUrl(
            prepared!!,
            "${PreviewBundleBuilder.PREVIEW_ORIGIN}logo.png"
        )
        assertNotNull(pngAsset)
        assertEquals("image/png", pngAsset!!.mimeType)
        assertEquals(4, pngAsset.rawBytes?.size)

        val cssAsset = PreviewBundleBuilder.resolveAssetForUrl(
            prepared,
            "${PreviewBundleBuilder.PREVIEW_ORIGIN}./style.css"
        )
        assertNotNull(cssAsset)
        assertEquals("text/css", cssAsset!!.mimeType)
    }

    @Test
    fun `gemini prompt payload receives actual sweet bakery files and 12 detected errors`() {
        val sample = ZipProjectImporter.createSampleBrokenProjectFiles()
        val projectFiles = sample.files.map {
            ProjectFile(
                id = it.relativePath,
                userId = "u1",
                projectId = "p1",
                relativePath = it.relativePath,
                fileType = it.fileType.name,
                sizeBytes = it.sizeBytes,
                originalContent = it.textContent
            )
        }
        val report = StaticWebsiteInspector.inspect(projectFiles, useRepaired = false, "u1", "p1")
        assertEquals(12, report.issues.size)

        val codeFiles = projectFiles.filter {
            val t = it.fileTypeEnum()
            t == FileType.HTML || t == FileType.CSS || t == FileType.JS
        }
        val payload = AiRepairRepository().buildProjectPromptPayload(codeFiles, report.issues)

        // Confirm prompt payload contains actual file contents and all 12 error codes/titles
        assertTrue(payload.contains("DETECTED ERRORS (12):"))
        assertTrue(payload.contains("PROJECT FILES (3):"))
        assertTrue(payload.contains("=== FILE: index.html (HTML) ==="))
        assertTrue(payload.contains("Welcome to Sweet Bakery"))
        assertTrue(payload.contains("=== FILE: style.css (CSS) ==="))
        assertTrue(payload.contains("width: 960px;"))
        assertTrue(payload.contains("=== FILE: app.js (JS) ==="))
        assertTrue(payload.contains("document.getElementByID"))
        report.issues.forEach { issue ->
            assertTrue("Expected error ${issue.code} in Gemini payload", payload.contains(issue.code))
        }
    }

    @Test
    fun `sweet bakery zip download contains repaired html css js and undo restores originals`() {
        val sample = ZipProjectImporter.createSampleBrokenProjectFiles()
        val projectFiles = sample.files.map {
            ProjectFile(
                id = it.relativePath,
                userId = "u1",
                projectId = "p1",
                relativePath = it.relativePath,
                fileType = it.fileType.name,
                sizeBytes = it.sizeBytes,
                originalContent = it.textContent
            )
        }

        // Simulate Gemini returning custom repaired HTML/CSS/JS overrides and verify WebsiteAutoRepairer merges & validates
        val geminiOverrides = mapOf(
            "app.js" to projectFiles.first { it.relativePath == "app.js" }.originalContent
                .replace("document.getElementByID", "document.getElementById")
                .plus("\n// Repaired by Gemini AI\n")
        )
        val outcome = WebsiteAutoRepairer.repairAndValidate(
            files = projectFiles,
            userId = "u1",
            projectId = "p1",
            geminiOverrides = geminiOverrides
        )

        // Verify HTML, CSS, and JS all actually changed while originalContent stayed intact
        assertTrue(outcome.updatedFiles.first { it.relativePath == "index.html" }.hasChanges())
        assertTrue(outcome.updatedFiles.first { it.relativePath == "style.css" }.hasChanges())
        assertTrue(outcome.updatedFiles.first { it.relativePath == "app.js" }.hasChanges())

        // Export repaired ZIP and inspect entries with ZipInputStream
        val repairedZip = ZipProjectImporter.exportProjectZipBytes(outcome.updatedFiles, useRepaired = true)
        val zipEntries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(repairedZip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertTrue(zipEntries.containsKey("index.html"))
        assertTrue(zipEntries.containsKey("style.css"))
        assertTrue(zipEntries.containsKey("app.js"))
        assertTrue(zipEntries.containsKey("images/bakery-logo.svg"))

        val exportedHtml = zipEntries.getValue("index.html")
        val exportedCss = zipEntries.getValue("style.css")
        val exportedJs = zipEntries.getValue("app.js")
        assertTrue(exportedHtml.contains("name=\"viewport\""))
        assertTrue(exportedHtml.contains("href=\"style.css\""))
        assertTrue(exportedHtml.contains("src=\"images/bakery-logo.svg\""))
        assertTrue(exportedCss.contains("FixMyApp Responsive Guard"))
        assertFalse(exportedJs.contains("getElementByID"))
        assertTrue(exportedJs.contains("getElementById"))
        assertTrue(exportedJs.contains("Repaired by Gemini AI"))

        // Verify Undo Changes restores exact original files byte-for-byte
        val restoredFiles = outcome.updatedFiles.map { it.copy(repairedContent = null) }
        val backupZip = ZipProjectImporter.exportProjectZipBytes(restoredFiles, useRepaired = false)
        val backupEntries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(backupZip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                backupEntries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertEquals(
            projectFiles.first { it.relativePath == "index.html" }.originalContent,
            backupEntries.getValue("index.html")
        )
        assertEquals(
            projectFiles.first { it.relativePath == "style.css" }.originalContent,
            backupEntries.getValue("style.css")
        )
        assertEquals(
            projectFiles.first { it.relativePath == "app.js" }.originalContent,
            backupEntries.getValue("app.js")
        )
    }
}
