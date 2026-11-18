package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.engine.StaticWebsiteInspector
import com.example.engine.WebsiteAutoRepairer
import com.example.engine.ZipProjectImporter
import com.example.model.AppLanguage
import com.example.model.FileType
import com.example.model.ProjectFile
import com.example.model.ProjectIssue
import com.example.model.ProjectStatus
import com.example.model.WebsiteProject
import com.example.ui.PreviewScreen
import com.example.ui.WorkspaceUiFeedback
import com.example.ui.theme.FixMyAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PreviewScreenRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sweet bakery repair workflow inspects 12 errors, runs fix automatically, previews fixed website, and undoes changes`() {
        val sample = ZipProjectImporter.createSampleBrokenProjectFiles()
        var currentFiles by mutableStateOf(
            sample.files.map {
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
        )
        var currentIssues by mutableStateOf<List<ProjectIssue>>(emptyList())
        var useRepaired by mutableStateOf(false)
        var telemetryText by mutableStateOf<String?>(null)
        var project by mutableStateOf(
            WebsiteProject(
                id = "p1",
                userId = "u1",
                name = sample.suggestedName,
                status = ProjectStatus.UPLOADED.name,
                fileCount = currentFiles.size.toLong(),
                totalBytes = sample.totalBytes
            )
        )

        composeTestRule.setContent {
            FixMyAppTheme {
                PreviewScreen(
                    language = AppLanguage.EN,
                    activeProject = project,
                    files = currentFiles,
                    issues = currentIssues,
                    useRepaired = useRepaired,
                    feedback = WorkspaceUiFeedback(repairTelemetry = telemetryText),
                    onTogglePreviewMode = { useRepaired = it },
                    onInspect = {
                        val report = StaticWebsiteInspector.inspect(currentFiles, useRepaired, "u1", "p1")
                        currentIssues = report.issues
                        project = project.copy(
                            status = ProjectStatus.INSPECTED.name,
                            errorsDetected = report.issues.size.toLong(),
                            remainingErrors = report.issues.size.toLong()
                        )
                    },
                    onRepairAll = {
                        val outcome = WebsiteAutoRepairer.repairAndValidate(currentFiles, "u1", "p1")
                        currentFiles = outcome.updatedFiles
                        currentIssues = outcome.resolvedIssues + outcome.remainingIssues
                        useRepaired = true
                        telemetryText = "Gemini AI received 3 code files & ${outcome.beforeReport.issues.size} errors • Modified: index.html, style.css, app.js • Validated & saved."
                        project = project.copy(
                            status = ProjectStatus.REPAIRED.name,
                            errorsDetected = outcome.beforeReport.issues.size.toLong(),
                            errorsFixed = outcome.resolvedIssues.size.toLong(),
                            remainingErrors = outcome.remainingIssues.size.toLong()
                        )
                    },
                    onUndo = {
                        val restored = currentFiles.map { it.copy(repairedContent = null) }
                        val baseline = StaticWebsiteInspector.inspect(restored, useRepaired = false, "u1", "p1")
                        currentFiles = restored
                        currentIssues = baseline.issues
                        useRepaired = false
                        telemetryText = null
                        project = project.copy(
                            status = ProjectStatus.INSPECTED.name,
                            errorsDetected = baseline.issues.size.toLong(),
                            errorsFixed = 0L,
                            remainingErrors = baseline.issues.size.toLong()
                        )
                    },
                    onLoadSample = {},
                    onGoToWorkspace = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Before inspection: WebView + Reload Preview + Original mode active
        composeTestRule.onNodeWithTag("preview_webview").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_reload_button").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("preview_mode_original").assertIsSelected()
        composeTestRule.onNodeWithTag("preview_mode_repaired").assertIsNotEnabled()

        // 2. Run inspection & error detection directly from Preview
        composeTestRule.onNodeWithTag("preview_inspect_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Preview MUST stay mounted & working after 12 errors are detected
        composeTestRule.onNodeWithTag("preview_errors_header").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Detected Errors (12)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_webview").performScrollTo().assertIsDisplayed()

        // 3. Reload Preview works cleanly without crashing
        composeTestRule.onNodeWithTag("preview_reload_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_webview").assertIsDisplayed()

        // 4. Tap Fix Automatically -> HTML, CSS, and JS are modified and Preview Fixed Website loads
        composeTestRule.onNodeWithTag("preview_repair_all_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("preview_mode_repaired")
            .performScrollTo()
            .assertIsEnabled()
            .assertIsSelected()
        composeTestRule.onNodeWithTag("preview_repair_telemetry_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_webview").assertIsDisplayed()
        assertTrue(currentFiles.first { it.relativePath == "index.html" }.hasChanges())
        assertTrue(currentFiles.first { it.relativePath == "style.css" }.hasChanges())
        assertTrue(currentFiles.first { it.relativePath == "app.js" }.hasChanges())
        assertFalse(currentFiles.first { it.relativePath == "app.js" }.activeContent().contains("getElementByID"))
        assertTrue(currentFiles.first { it.relativePath == "app.js" }.activeContent().contains("document.getElementById('buyBtn')"))

        // 5. Tap Undo Changes -> restores original files and returns preview to Original (Before)
        composeTestRule.onNodeWithTag("preview_undo_button").performScrollTo().assertIsEnabled().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("preview_mode_original").performScrollTo().assertIsSelected()
        composeTestRule.onNodeWithTag("preview_mode_repaired").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("preview_webview").assertIsDisplayed()
        assertTrue(currentFiles.all { !it.hasChanges() && it.repairedContent == null })
        assertEquals(12, currentIssues.size)
        assertEquals(
            sample.files.first { it.relativePath == "app.js" }.textContent,
            currentFiles.first { it.relativePath == "app.js" }.activeContent()
        )
    }

    @Test
    fun `preview shows clear error message when no HTML entry page exists`() {
        val cssOnlyFiles = listOf(
            ProjectFile(
                id = "css1",
                userId = "u1",
                projectId = "p2",
                relativePath = "style.css",
                fileType = FileType.CSS.name,
                sizeBytes = 20L,
                originalContent = "body { color: red; }"
            )
        )
        val project = WebsiteProject(
            id = "p2",
            userId = "u1",
            name = "CSS Only Project",
            fileCount = 1L
        )

        composeTestRule.setContent {
            FixMyAppTheme {
                PreviewScreen(
                    language = AppLanguage.EN,
                    activeProject = project,
                    files = cssOnlyFiles,
                    issues = emptyList(),
                    useRepaired = false,
                    feedback = WorkspaceUiFeedback(),
                    onTogglePreviewMode = {},
                    onInspect = {},
                    onRepairAll = {},
                    onUndo = {},
                    onLoadSample = {},
                    onGoToWorkspace = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_error_panel").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Preview could not load: No HTML entry file (.html) was found in this project."
        ).assertIsDisplayed()
    }
}
