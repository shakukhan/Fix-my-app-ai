package com.example.ui.components

import android.view.KeyEvent

/**
 * Guards against duplicate non-repeat [KeyEvent.ACTION_DOWN] and single-character
 * [KeyEvent.ACTION_MULTIPLE] events emitted by bridged desktop/emulator keyboards
 * or duplicate input dispatchers while allowing genuine repeated letters
 * (such as "ll" in "hello" or "ee" in "sweet") and held-key auto-repeats.
 */
class KeyEventDeduplicator(
    private val burstWindowMs: Long = DEFAULT_BURST_WINDOW_MS
) {
    private val activeDownTimes = mutableMapOf<Int, Long>()
    private val lastCompletedDownTimes = mutableMapOf<Int, Long>()
    private val lastAcceptedEventTimes = mutableMapOf<Int, Long>()
    private var lastCommittedChar: Int = 0
    private var lastCommittedCharTimeMs: Long = 0L

    @Suppress("DEPRECATION")
    fun shouldDrop(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            val chars = event.characters
            if (!chars.isNullOrEmpty() && chars.length == 1) {
                val ch = chars[0].code
                val now = event.eventTime
                if (now > 0L && ch == lastCommittedChar && (now - lastCommittedCharTimeMs) in 0L..burstWindowMs) {
                    return true
                }
                lastCommittedChar = ch
                if (now > 0L) lastCommittedCharTimeMs = now
            }
            return false
        }

        return shouldDrop(
            action = event.action,
            keyCode = event.keyCode,
            repeatCount = event.repeatCount,
            downTime = event.downTime,
            eventTime = event.eventTime,
            isTextEditingKey = isTrackedTextKey(event),
            isDeletionKey = isDeleteKey(event.keyCode),
            unicodeChar = event.unicodeChar
        )
    }

    internal fun shouldDrop(
        action: Int,
        keyCode: Int,
        repeatCount: Int,
        downTime: Long,
        eventTime: Long = downTime,
        isTextEditingKey: Boolean,
        isDeletionKey: Boolean = false,
        unicodeChar: Int = 0
    ): Boolean {
        if (!isTextEditingKey || keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false
        }

        return when (action) {
            KeyEvent.ACTION_UP -> {
                val activeDown = activeDownTimes.remove(keyCode)
                if (activeDown != null) {
                    lastCompletedDownTimes[keyCode] = activeDown
                }
                false
            }
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount > 0) {
                    return false
                }
                val alreadyDown = activeDownTimes.containsKey(keyCode)
                val replayedSamePress = downTime > 0L && lastCompletedDownTimes[keyCode] == downTime
                val prevEventTime = lastAcceptedEventTimes[keyCode]
                val rapidDuplicateBurst = !isDeletionKey &&
                    eventTime > 0L &&
                    prevEventTime != null &&
                    (eventTime - prevEventTime) in 0L..burstWindowMs

                val sameCharRapidBurst = !isDeletionKey &&
                    unicodeChar != 0 &&
                    eventTime > 0L &&
                    unicodeChar == lastCommittedChar &&
                    (eventTime - lastCommittedCharTimeMs) in 0L..burstWindowMs

                if (alreadyDown || replayedSamePress || rapidDuplicateBurst || sameCharRapidBurst) {
                    true
                } else {
                    activeDownTimes[keyCode] = downTime
                    if (eventTime > 0L) {
                        lastAcceptedEventTimes[keyCode] = eventTime
                        if (!isDeletionKey && unicodeChar != 0) {
                            lastCommittedChar = unicodeChar
                            lastCommittedCharTimeMs = eventTime
                        }
                    }
                    false
                }
            }
            else -> false
        }
    }

    private fun isTrackedTextKey(event: KeyEvent): Boolean {
        return event.unicodeChar != 0 ||
            isDeleteKey(event.keyCode) ||
            event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_SPACE
    }

    private fun isDeleteKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL
    }

    companion object {
        const val DEFAULT_BURST_WINDOW_MS = 40L
    }
}
