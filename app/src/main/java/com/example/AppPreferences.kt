package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val doubleTapWindowMs: Int = AppPreferences.DEFAULT_DOUBLE_TAP_WINDOW_MS,
    val isShortcutsPaused: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val hasAccessibilityConsent: Boolean = false,
)

class AppPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun readSettings(): UserSettings = UserSettings(
        doubleTapWindowMs = prefs.getInt(KEY_DOUBLE_TAP_WINDOW_MS, DEFAULT_DOUBLE_TAP_WINDOW_MS)
            .coerceIn(MIN_DOUBLE_TAP_WINDOW_MS, MAX_DOUBLE_TAP_WINDOW_MS),
        isShortcutsPaused = prefs.getBoolean(KEY_SHORTCUTS_PAUSED, false),
        hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
        hasAccessibilityConsent = prefs.getBoolean(KEY_ACCESSIBILITY_CONSENT, false),
    )

    fun setDoubleTapWindowMs(windowMs: Int) {
        val clamped = windowMs.coerceIn(MIN_DOUBLE_TAP_WINDOW_MS, MAX_DOUBLE_TAP_WINDOW_MS)
        prefs.edit().putInt(KEY_DOUBLE_TAP_WINDOW_MS, clamped).apply()
        _settings.value = _settings.value.copy(doubleTapWindowMs = clamped)
    }

    fun setShortcutsPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_SHORTCUTS_PAUSED, paused).apply()
        _settings.value = _settings.value.copy(isShortcutsPaused = paused)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        _settings.value = _settings.value.copy(hapticFeedbackEnabled = enabled)
    }

    fun setAccessibilityConsent(consented: Boolean) {
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_CONSENT, consented).apply()
        _settings.value = _settings.value.copy(hasAccessibilityConsent = consented)
    }

    fun refresh() {
        _settings.value = readSettings()
    }

    companion object {
        private const val PREFS_NAME = "double_tap_flashlight_prefs"
        private const val KEY_DOUBLE_TAP_WINDOW_MS = "double_tap_window_ms"
        private const val KEY_SHORTCUTS_PAUSED = "shortcuts_paused"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
        private const val KEY_ACCESSIBILITY_CONSENT = "has_accessibility_consent"

        const val MIN_DOUBLE_TAP_WINDOW_MS = 250
        const val MAX_DOUBLE_TAP_WINDOW_MS = 700
        const val DEFAULT_DOUBLE_TAP_WINDOW_MS = 425

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context).also { instance = it }
            }
        }
    }
}
