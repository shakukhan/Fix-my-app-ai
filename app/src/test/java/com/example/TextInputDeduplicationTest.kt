package com.example

import android.view.KeyEvent
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import com.example.model.AppLanguage
import com.example.model.ReadinessCheck
import com.example.model.WebsiteProject
import com.example.ui.AuthScreen
import com.example.ui.DeployScreen
import com.example.ui.WorkspaceScreen
import com.example.ui.WorkspaceUiFeedback
import com.example.ui.components.DeduplicatingInputTransformation
import com.example.ui.components.KeyEventDeduplicator
import com.example.ui.theme.FixMyAppTheme
import com.google.firebase.FirebaseApp
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
class TextInputDeduplicationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `key deduplicator blocks same-downTime and incremented-time duplicate bursts while keeping genuine hello ll`() {
        val deduplicator = KeyEventDeduplicator(burstWindowMs = 35L)
        val typed = StringBuilder()

        fun dispatchKey(action: Int, keyCode: Int, ch: Char, downTime: Long, eventTime: Long = downTime, repeatCount: Int = 0) {
            val dropped = deduplicator.shouldDrop(
                action = action,
                keyCode = keyCode,
                repeatCount = repeatCount,
                downTime = downTime,
                eventTime = eventTime,
                isTextEditingKey = true
            )
            if (!dropped && action == KeyEvent.ACTION_DOWN) {
                typed.append(ch)
            }
        }

        // Simulate a bridged desktop/emulator keyboard double-firing DOWN+UP pairs 4ms apart with incremented timestamps
        val bakeryKeys = listOf(
            KeyEvent.KEYCODE_B to 'b',
            KeyEvent.KEYCODE_A to 'a',
            KeyEvent.KEYCODE_K to 'k',
            KeyEvent.KEYCODE_E to 'e',
            KeyEvent.KEYCODE_R to 'r',
            KeyEvent.KEYCODE_Y to 'y'
        )
        bakeryKeys.forEachIndexed { idx, (code, ch) ->
            val t = (idx + 1) * 100L
            dispatchKey(KeyEvent.ACTION_DOWN, code, ch, downTime = t, eventTime = t)
            dispatchKey(KeyEvent.ACTION_UP, code, ch, downTime = t, eventTime = t + 1)
            // Duplicate event 4ms later with a brand new downTime!
            dispatchKey(KeyEvent.ACTION_DOWN, code, ch, downTime = t + 4, eventTime = t + 4)
            dispatchKey(KeyEvent.ACTION_UP, code, ch, downTime = t + 4, eventTime = t + 5)
        }
        assertEquals("bakery", typed.toString())

        // Reset buffer and test "hello" (must keep genuine 'll', must NOT become "hheelllloo")
        typed.clear()
        val helloKeys = listOf(
            Triple(KeyEvent.KEYCODE_H, 'h', 1000L),
            Triple(KeyEvent.KEYCODE_E, 'e', 1100L),
            Triple(KeyEvent.KEYCODE_L, 'l', 1200L),
            Triple(KeyEvent.KEYCODE_L, 'l', 1300L),
            Triple(KeyEvent.KEYCODE_O, 'o', 1400L)
        )
        for ((code, ch, t) in helloKeys) {
            dispatchKey(KeyEvent.ACTION_DOWN, code, ch, downTime = t, eventTime = t)
            dispatchKey(KeyEvent.ACTION_DOWN, code, ch, downTime = t, eventTime = t + 2)
            dispatchKey(KeyEvent.ACTION_UP, code, ch, downTime = t, eventTime = t + 10)
        }
        assertEquals("hello", typed.toString())
    }

    @Test
    fun `key deduplicator allows zero-timestamp synthetic double letters while blocking stuck-down duplicates`() {
        val deduplicator = KeyEventDeduplicator()
        assertFalse(deduplicator.shouldDrop(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_L, 0, 0L, 0L, true))
        assertTrue(deduplicator.shouldDrop(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_L, 0, 0L, 0L, true))
        assertFalse(deduplicator.shouldDrop(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_L, 0, 0L, 0L, true))
        assertFalse(deduplicator.shouldDrop(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_L, 0, 0L, 0L, true))
        assertFalse(deduplicator.shouldDrop(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_L, 0, 0L, 0L, true))
    }

    @Test
    fun `key deduplicator types manisha at gmail once even when every keystroke fires twice`() {
        val deduplicator = KeyEventDeduplicator(burstWindowMs = 40L)
        val out = StringBuilder()
        "manisha@gmail.com".forEachIndexed { idx, ch ->
            val t = (idx + 1) * 100L
            val code = when (ch) {
                '@' -> KeyEvent.KEYCODE_2
                '.' -> KeyEvent.KEYCODE_PERIOD
                else -> KeyEvent.KEYCODE_A + (ch - 'a')
            }
            if (!deduplicator.shouldDrop(KeyEvent.ACTION_DOWN, code, 0, t, t, isTextEditingKey = true, unicodeChar = ch.code)) {
                out.append(ch)
            }
            if (!deduplicator.shouldDrop(KeyEvent.ACTION_DOWN, code, 0, t + 5, t + 5, isTextEditingKey = true, unicodeChar = ch.code)) {
                out.append(ch)
            }
            deduplicator.shouldDrop(KeyEvent.ACTION_UP, code, 0, t, t + 10, isTextEditingKey = true, unicodeChar = ch.code)
        }
        assertEquals("manisha@gmail.com", out.toString())
    }

    @Test
    fun `input transformation rejects rapid double commits for manisha at gmail and keeps human double letters`() {
        var fakeClockMs = 1000L
        val transform = DeduplicatingInputTransformation(
            maxLength = 80,
            burstWindowMs = 40L,
            nowMs = { fakeClockMs }
        )
        val state = TextFieldState()

        fun typeChar(ch: Char, advanceMs: Long) {
            fakeClockMs += advanceMs
            state.edit {
                insert(length, ch.toString())
                with(transform) { transformInput() }
            }
        }

        // Each letter of "manisha@gmail.com" gets double-committed 5ms apart (e.g., KeyEvent + IME commitText)
        for (ch in "manisha@gmail.com") {
            typeChar(ch, advanceMs = 100L)
            typeChar(ch, advanceMs = 5L) // duplicate within burstWindowMs -> reverted
        }
        assertEquals("manisha@gmail.com", state.text.toString())

        // Each letter of "bakery" gets double-committed 10ms apart -> still "bakery"
        state.edit { replace(0, length, "") }
        for (ch in "bakery") {
            typeChar(ch, advanceMs = 100L)
            typeChar(ch, advanceMs = 10L)
        }
        assertEquals("bakery", state.text.toString())

        // Genuine "hello" with 90ms between the two 'l' presses stays "hello"
        state.edit { replace(0, length, "") }
        typeChar('h', 100L)
        typeChar('e', 100L)
        typeChar('l', 100L)
        typeChar('l', 90L)
        typeChar('o', 100L)
        assertEquals("hello", state.text.toString())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `workspace project name input enters manisha email, website URL, bakery, and hello once with cursor preservation`() {
        composeTestRule.setContent {
            FixMyAppTheme {
                WorkspaceScreen(
                    language = AppLanguage.EN,
                    activeProject = null,
                    files = emptyList(),
                    issues = emptyList(),
                    feedback = WorkspaceUiFeedback(),
                    onUploadUris = { _, _ -> },
                    onLoadSample = {},
                    onInspect = {},
                    onRepair = {},
                    onUndo = {},
                    onAiDeepFix = { _, _ -> },
                    onExportZip = { _, _, _ -> },
                    onSelectCategory = {},
                    onSelectDiffFile = {},
                    onNavigateToPreview = {},
                    onNavigateToDeploy = {},
                    onDismissBanner = {}
                )
            }
        }

        val input = composeTestRule.onNodeWithTag("project_name_input")
        input.performScrollTo().assertIsDisplayed()

        // 1. Type "manisha@gmail.com" -> must be exactly "manisha@gmail.com"
        input.performTextInput("manisha@gmail.com")
        composeTestRule.waitForIdle()
        assertEquals(
            "manisha@gmail.com",
            input.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        )

        // 2. Clear and type website URL -> must be exact URL
        input.performTextClearance()
        input.performTextInput("https://sweetbakery.netlify.app")
        composeTestRule.waitForIdle()
        assertEquals(
            "https://sweetbakery.netlify.app",
            input.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        )

        // 3. Clear and type "bakery" -> must be "bakery", never "bbaakkeerryy"
        input.performTextClearance()
        input.performTextInput("bakery")
        composeTestRule.waitForIdle()
        var semanticsNode = input.fetchSemanticsNode()
        assertEquals(
            "bakery",
            semanticsNode.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        )

        // 4. Preserve cursor in middle (index 3, between "bak" and "ery") and insert "-"
        input.performTextInputSelection(TextRange(3))
        input.performTextInput("-")
        composeTestRule.waitForIdle()
        semanticsNode = input.fetchSemanticsNode()
        assertEquals(
            "bak-ery",
            semanticsNode.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        )
        assertEquals(
            TextRange(4),
            semanticsNode.config[androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange]
        )

        // 5. Clear & type "hello" -> must be "hello", never "hheelllloo"
        input.performTextClearance()
        input.performTextInput("hello")
        composeTestRule.waitForIdle()
        assertEquals(
            "hello",
            input.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        )

        // 6. Paste/replace long text -> respects 80-char maxLength cleanly
        val longName = "Sweet Bakery " + "X".repeat(90)
        input.performTextReplacement(longName)
        composeTestRule.waitForIdle()
        val storedText = input.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertEquals(80, storedText.length)
        assertTrue(storedText.startsWith("Sweet Bakery "))
    }

    @Test
    fun `deploy screen password token input enters text once without duplication and passes exact token`() {
        var submittedToken = ""
        val project = WebsiteProject(
            id = "p1",
            userId = "u1",
            name = "Sweet Bakery",
            fileCount = 3L
        )
        composeTestRule.setContent {
            FixMyAppTheme {
                DeployScreen(
                    language = AppLanguage.EN,
                    activeProject = project,
                    readinessChecks = listOf(ReadinessCheck(key = "r1", passed = true, labelEn = "Entry HTML present", labelHi = "HTML")),
                    feedback = WorkspaceUiFeedback(),
                    onDeployNetlify = { _, token -> submittedToken = token }
                )
            }
        }

        val tokenInput = composeTestRule.onNodeWithTag("netlify_token_input")
        tokenInput.performScrollTo().assertIsDisplayed()
        tokenInput.performTextInput("S3cur3!Pass@manisha_2026")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("deploy_netlify_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("S3cur3!Pass@manisha_2026", submittedToken)

        tokenInput.performScrollTo().performTextClearance()
        tokenInput.performTextReplacement("nfp_pasted_token_999")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("deploy_netlify_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("nfp_pasted_token_999", submittedToken)
    }

    @Test
    fun `auth screen renders google sign in button cleanly`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        composeTestRule.setContent {
            FixMyAppTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    }
}
