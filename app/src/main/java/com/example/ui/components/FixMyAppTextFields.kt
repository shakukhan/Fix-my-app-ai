package com.example.ui.components

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Prevents duplicate single-character commits (such as both a KeyEvent and an IME
 * commitText firing within a few milliseconds) while preserving pastes (length > 1),
 * deletions, mid-string edits, and genuine human double letters ("ll" in "hello").
 */
@OptIn(ExperimentalFoundationApi::class)
internal class DeduplicatingInputTransformation(
    private val maxLength: Int? = null,
    private val burstWindowMs: Long = 40L,
    private val nowMs: () -> Long = { SystemClock.uptimeMillis() }
) : InputTransformation {
    private var lastSingleChar: Char? = null
    private var lastInsertEnd: Int = -1
    private var lastInsertUptimeMs: Long = 0L

    override fun TextFieldBuffer.transformInput() {
        if (changes.changeCount == 1) {
            val newRange = changes.getRange(0)
            val oldRange = changes.getOriginalRange(0)
            val now = nowMs()
            val elapsed = now - lastInsertUptimeMs
            if (oldRange.length == 0 && newRange.length == 1) {
                val insertedChar = asCharSequence()[newRange.start]
                if (lastSingleChar == insertedChar &&
                    lastInsertEnd == newRange.start &&
                    elapsed in 0L..burstWindowMs
                ) {
                    revertAllChanges()
                    return
                }
                lastSingleChar = insertedChar
                lastInsertEnd = newRange.end
                lastInsertUptimeMs = now
            } else if (oldRange.length == 1 && newRange.length == 2 &&
                newRange.start + 1 < length &&
                asCharSequence()[newRange.start] == lastSingleChar &&
                asCharSequence()[newRange.start + 1] == lastSingleChar &&
                elapsed in 0L..burstWindowMs
            ) {
                revertAllChanges()
                return
            } else {
                resetBurstTracking()
            }
        } else if (changes.changeCount > 1) {
            resetBurstTracking()
        }

        if (maxLength != null && length > maxLength) {
            delete(maxLength, length)
        }
    }

    private fun resetBurstTracking() {
        lastSingleChar = null
        lastInsertEnd = -1
    }
}

/**
 * State-based Material 3 outlined text field backed by [TextFieldState].
 *
 * Uses Compose Foundation's state-based [BasicTextField] / [BasicSecureTextField]
 * instead of the legacy `String` + `onValueChange` callback loop, preventing IME
 * composition feedback loops, duplicate character commits, and cursor jumps during
 * recomposition inside scrollable lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixMyAppOutlinedTextField(
    state: TextFieldState,
    labelText: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    maxLength: Int? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    val textColor = MaterialTheme.colorScheme.onSurface
    val textStyle = LocalTextStyle.current.merge(
        MaterialTheme.typography.bodyLarge.copy(color = textColor)
    )
    val cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
    val inputTransformation = remember(maxLength) {
        DeduplicatingInputTransformation(maxLength = maxLength)
    }
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    if (isPassword) {
        BasicSecureTextField(
            state = state,
            modifier = taggedModifier,
            enabled = enabled,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            inputTransformation = inputTransformation,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorator = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = state.text.toString(),
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    label = { Text(labelText) },
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = colors
                        )
                    }
                )
            }
        )
    } else {
        BasicTextField(
            state = state,
            modifier = taggedModifier,
            enabled = enabled,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = inputTransformation,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorator = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = state.text.toString(),
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    label = { Text(labelText) },
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = colors
                        )
                    }
                )
            }
        )
    }
}
