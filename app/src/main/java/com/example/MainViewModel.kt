package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AccessibilityStatus {
    ACTIVE,
    PAUSED,
    INTERRUPTED,
    DISABLED,
}

data class MainUiState(
    val flashState: FlashState = FlashState(),
    val hasCameraPermission: Boolean = false,
    val accessibilityStatus: AccessibilityStatus = AccessibilityStatus.DISABLED,
    val userSettings: UserSettings = UserSettings(),
    val showConsentDialog: Boolean = false,
    val statusFeedbackResId: Int? = null,
    val customErrorDetail: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val flashController = FlashController.getInstance(appContext)
    private val preferences = AppPreferences.getInstance(appContext)

    private val permissionSnapshot = MutableStateFlow(readPermissions())
    private val showConsentDialog = MutableStateFlow(false)
    private val statusFeedbackResId = MutableStateFlow<Int?>(null)
    private val customErrorDetail = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        flashController.flashState,
        permissionSnapshot,
        VolumeKeyService.serviceState,
        preferences.settings,
        showConsentDialog,
    ) { flash, perms, serviceConn, settings, dialogVisible ->
        val accessStatus = when {
            !perms.accessibilityEnabled && serviceConn != ServiceConnectionState.CONNECTED ->
                AccessibilityStatus.DISABLED
            serviceConn == ServiceConnectionState.INTERRUPTED ->
                AccessibilityStatus.INTERRUPTED
            settings.isShortcutsPaused ->
                AccessibilityStatus.PAUSED
            else ->
                AccessibilityStatus.ACTIVE
        }
        MainUiState(
            flashState = flash,
            hasCameraPermission = perms.cameraGranted,
            accessibilityStatus = accessStatus,
            userSettings = settings,
            showConsentDialog = dialogVisible,
            statusFeedbackResId = statusFeedbackResId.value,
            customErrorDetail = customErrorDetail.value,
        )
    }.combine(statusFeedbackResId) { state, feedbackRes ->
        state.copy(statusFeedbackResId = feedbackRes)
    }.combine(customErrorDetail) { state, detail ->
        state.copy(customErrorDetail = detail)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(),
    )

    private data class PermissionSnapshot(
        val cameraGranted: Boolean,
        val accessibilityEnabled: Boolean,
    )

    private fun readPermissions(): PermissionSnapshot = PermissionSnapshot(
        cameraGranted = PermissionManager.hasCameraPermission(appContext),
        accessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(appContext),
    )

    fun refreshStatus() {
        preferences.refresh()
        flashController.refreshHardwareState()
        permissionSnapshot.value = readPermissions()
    }

    fun onTurnOnClicked() {
        clearTransientFeedback()
        flashController.turnOn()
    }

    fun onTurnOffClicked() {
        clearTransientFeedback()
        flashController.turnOff()
    }

    fun onIndicatorToggled() {
        clearTransientFeedback()
        flashController.toggle()
    }

    fun onTestFlashlightClicked() {
        clearTransientFeedback()
        flashController.testFlashlight(viewModelScope) { succeeded ->
            if (succeeded) {
                statusFeedbackResId.value = R.string.info_test_completed
            }
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        permissionSnapshot.value = readPermissions()
        if (granted) {
            flashController.clearError()
            flashController.refreshHardwareState()
        } else {
            statusFeedbackResId.value = R.string.error_camera_permission_denied
        }
    }

    fun onEnableAccessibilityClicked(onLaunchSettings: () -> Unit) {
        clearTransientFeedback()
        if (preferences.settings.value.hasAccessibilityConsent) {
            onLaunchSettings()
        } else {
            showConsentDialog.value = true
        }
    }

    fun onDismissConsentDialog() {
        showConsentDialog.value = false
    }

    fun onConsentConfirmed(onLaunchSettings: () -> Unit) {
        preferences.setAccessibilityConsent(true)
        showConsentDialog.value = false
        onLaunchSettings()
    }

    fun onSettingsLaunchFailed() {
        statusFeedbackResId.value = R.string.error_settings_launch_failed
    }

    fun dismissError() {
        flashController.clearError()
        clearTransientFeedback()
    }

    fun updateDoubleTapWindowMs(windowMs: Int) {
        preferences.setDoubleTapWindowMs(windowMs)
    }

    fun toggleShortcutsPaused(paused: Boolean) {
        preferences.setShortcutsPaused(paused)
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        preferences.setHapticFeedbackEnabled(enabled)
    }

    fun resetAccessibilityConsent() {
        preferences.setAccessibilityConsent(false)
    }

    private fun clearTransientFeedback() {
        viewModelScope.launch {
            statusFeedbackResId.value = null
            customErrorDetail.value = null
        }
    }
}
