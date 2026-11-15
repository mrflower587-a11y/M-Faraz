package com.example

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Double Tap Flashlight", appName)
    }

    @Test
    fun `double press volume up triggers turn on and resets timer`() {
        val detector = DoubleTapDetector(windowMsProvider = { 425L })

        // First press
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 100L, 100L),
        )
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, 150L, 100L),
        )

        // Second quick press within 425ms
        assertEquals(
            DoubleTapAction.TURN_ON_FLASH,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 300L, 300L),
        )
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, 340L, 300L),
        )

        // Third quick press should NOT immediately re-trigger because timer reset
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 420L, 420L),
        )
    }

    @Test
    fun `double press volume down triggers turn off`() {
        val detector = DoubleTapDetector(windowMsProvider = { 425L })

        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, 1000L, 1000L),
        )
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP, 0, 1060L, 1000L),
        )
        assertEquals(
            DoubleTapAction.TURN_OFF_FLASH,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, 1250L, 1250L),
        )
    }

    @Test
    fun `long press and repeat events do not trigger double tap`() {
        val detector = DoubleTapDetector(windowMsProvider = { 425L }, longPressTimeoutMs = 500L)

        // Initial press + repeat event
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 100L, 100L),
        )
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 1, 600L, 100L),
        )
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, 700L, 100L),
        )

        // Immediate follow-up single tap after long press must NOT trigger
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 800L, 800L),
        )
    }

    @Test
    fun `mixed volume up then down does not trigger`() {
        val detector = DoubleTapDetector(windowMsProvider = { 425L })

        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 100L, 100L),
        )
        assertEquals(
            DoubleTapAction.NONE,
            detector.onKeyEvent(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, 200L, 200L),
        )
    }

    @Test
    fun `preferences store and clamp double tap interval`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences.getInstance(context)

        prefs.setDoubleTapWindowMs(100)
        assertEquals(AppPreferences.MIN_DOUBLE_TAP_WINDOW_MS, prefs.settings.value.doubleTapWindowMs)

        prefs.setDoubleTapWindowMs(900)
        assertEquals(AppPreferences.MAX_DOUBLE_TAP_WINDOW_MS, prefs.settings.value.doubleTapWindowMs)

        prefs.setDoubleTapWindowMs(450)
        assertEquals(450, prefs.settings.value.doubleTapWindowMs)

        prefs.setAccessibilityConsent(true)
        assertTrue(prefs.settings.value.hasAccessibilityConsent)
        prefs.setAccessibilityConsent(false)
        assertFalse(prefs.settings.value.hasAccessibilityConsent)
    }
}
