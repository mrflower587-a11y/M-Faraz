package com.example

import android.view.KeyEvent

enum class DoubleTapAction {
    NONE,
    TURN_ON_FLASH,
    TURN_OFF_FLASH,
}

/**
 * Deterministic double-press detector for physical Volume Up and Volume Down hardware keys.
 * Rejects held-key repeats, long presses, and rapid bounce noise, and resets immediately after
 * a successful double-press so triple-taps do not accidentally re-trigger.
 */
class DoubleTapDetector(
    private val windowMsProvider: () -> Long,
    private val longPressTimeoutMs: Long = DEFAULT_LONG_PRESS_TIMEOUT_MS,
) {

    private var pendingKeyCode: Int? = null
    private var firstPressTimeMs: Long = 0L
    private var suppressedKeyCode: Int? = null

    fun onKeyEvent(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        eventTimeMs: Long,
        downTimeMs: Long,
    ): DoubleTapAction {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return DoubleTapAction.NONE
        }

        return when (action) {
            KeyEvent.ACTION_DOWN -> handleActionDown(
                keyCode = keyCode,
                repeatCount = repeatCount,
                eventTimeMs = eventTimeMs,
            )

            KeyEvent.ACTION_UP -> handleActionUp(
                keyCode = keyCode,
                eventTimeMs = eventTimeMs,
                downTimeMs = downTimeMs,
            )

            else -> DoubleTapAction.NONE
        }
    }

    private fun handleActionDown(
        keyCode: Int,
        repeatCount: Int,
        eventTimeMs: Long,
    ): DoubleTapAction {
        if (repeatCount > 0) {
            // Long-press repeat detected: clear any pending window and suppress until ACTION_UP.
            resetPending()
            suppressedKeyCode = keyCode
            return DoubleTapAction.NONE
        }

        if (suppressedKeyCode == keyCode) {
            return DoubleTapAction.NONE
        }

        val maxWindowMs = windowMsProvider().coerceIn(
            AppPreferences.MIN_DOUBLE_TAP_WINDOW_MS.toLong(),
            AppPreferences.MAX_DOUBLE_TAP_WINDOW_MS.toLong(),
        )

        val currentPending = pendingKeyCode
        if (currentPending == keyCode) {
            val delta = eventTimeMs - firstPressTimeMs
            if (delta in MIN_DEBOUNCE_MS..maxWindowMs) {
                resetPending()
                return if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    DoubleTapAction.TURN_ON_FLASH
                } else {
                    DoubleTapAction.TURN_OFF_FLASH
                }
            }
        }

        // Start a fresh detection window for this key press.
        pendingKeyCode = keyCode
        firstPressTimeMs = eventTimeMs
        return DoubleTapAction.NONE
    }

    private fun handleActionUp(
        keyCode: Int,
        eventTimeMs: Long,
        downTimeMs: Long,
    ): DoubleTapAction {
        if (suppressedKeyCode == keyCode) {
            suppressedKeyCode = null
            resetPending()
            return DoubleTapAction.NONE
        }

        val holdDuration = eventTimeMs - downTimeMs
        if (holdDuration >= longPressTimeoutMs && pendingKeyCode == keyCode) {
            resetPending()
        }
        return DoubleTapAction.NONE
    }

    fun reset() {
        resetPending()
        suppressedKeyCode = null
    }

    private fun resetPending() {
        pendingKeyCode = null
        firstPressTimeMs = 0L
    }

    companion object {
        private const val MIN_DEBOUNCE_MS = 25L
        private const val DEFAULT_LONG_PRESS_TIMEOUT_MS = 500L
    }
}
