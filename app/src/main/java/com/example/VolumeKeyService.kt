package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ServiceConnectionState {
    DISCONNECTED,
    CONNECTED,
    INTERRUPTED,
}

class VolumeKeyService : AccessibilityService() {

    private lateinit var preferences: AppPreferences
    private lateinit var flashController: FlashController
    private lateinit var detector: DoubleTapDetector

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences.getInstance(applicationContext)
        flashController = FlashController.getInstance(applicationContext)
        detector = DoubleTapDetector(
            windowMsProvider = { preferences.settings.value.doubleTapWindowMs.toLong() },
            longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(450L),
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.notificationTimeout = 100
        serviceInfo = info

        detector.reset()
        _serviceState.value = ServiceConnectionState.CONNECTED
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        val currentSettings = preferences.settings.value
        if (currentSettings.isShortcutsPaused) {
            detector.reset()
            return false
        }

        val action = detector.onKeyEvent(
            keyCode = keyCode,
            action = event.action,
            repeatCount = event.repeatCount,
            eventTimeMs = event.eventTime,
            downTimeMs = event.downTime,
        )

        when (action) {
            DoubleTapAction.TURN_ON_FLASH -> {
                val success = flashController.turnOn()
                if (success && currentSettings.hapticFeedbackEnabled) {
                    triggerTactilePulse()
                }
                _lastShortcutTriggered.value = ShortcutTriggerEvent(
                    action = DoubleTapAction.TURN_ON_FLASH,
                    succeeded = success,
                    timestampMs = System.currentTimeMillis(),
                )
            }

            DoubleTapAction.TURN_OFF_FLASH -> {
                val success = flashController.turnOff()
                if (success && currentSettings.hapticFeedbackEnabled) {
                    triggerTactilePulse()
                }
                _lastShortcutTriggered.value = ShortcutTriggerEvent(
                    action = DoubleTapAction.TURN_OFF_FLASH,
                    succeeded = success,
                    timestampMs = System.currentTimeMillis(),
                )
            }

            DoubleTapAction.NONE -> Unit
        }

        // Never consume volume key events so normal volume adjustment keeps working.
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately unused: this service does not read screen content or UI events.
    }

    override fun onInterrupt() {
        detector.reset()
        _serviceState.value = ServiceConnectionState.INTERRUPTED
    }

    override fun onUnbind(intent: Intent?): Boolean {
        detector.reset()
        _serviceState.value = ServiceConnectionState.DISCONNECTED
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        detector.reset()
        _serviceState.value = ServiceConnectionState.DISCONNECTED
        super.onDestroy()
    }

    private fun triggerTactilePulse() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25L)
            }
        } catch (_: SecurityException) {
            // Ignore if vibration is restricted by battery saver or OEM policy
        } catch (_: Exception) {
            // Prevent service crash on non-standard vibrator hardware
        }
    }

    data class ShortcutTriggerEvent(
        val action: DoubleTapAction,
        val succeeded: Boolean,
        val timestampMs: Long,
    )

    companion object {
        private val _serviceState = MutableStateFlow(ServiceConnectionState.DISCONNECTED)
        val serviceState: StateFlow<ServiceConnectionState> = _serviceState.asStateFlow()

        private val _lastShortcutTriggered = MutableStateFlow<ShortcutTriggerEvent?>(null)
        val lastShortcutTriggered: StateFlow<ShortcutTriggerEvent?> =
            _lastShortcutTriggered.asStateFlow()
    }
}
