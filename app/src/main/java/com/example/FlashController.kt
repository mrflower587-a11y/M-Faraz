package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FlashError {
    data object NoFlashHardware : FlashError
    data object CameraPermissionDenied : FlashError
    data object CameraInUse : FlashError
    data object TorchUnavailable : FlashError
    data object UnsupportedAndroidVersion : FlashError
    data class UnknownError(val detail: String) : FlashError
}

data class FlashState(
    val isTorchOn: Boolean = false,
    val isTorchAvailable: Boolean = true,
    val hasFlashHardware: Boolean = true,
    val cameraId: String? = null,
    val isTesting: Boolean = false,
    val lastError: FlashError? = null,
)

class FlashController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val cameraManager: CameraManager? =
        appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _flashState = MutableStateFlow(FlashState())
    val flashState: StateFlow<FlashState> = _flashState.asStateFlow()

    private var testPulseJob: Job? = null
    private var torchCallbackRegistered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            val current = _flashState.value
            if (current.cameraId == null || current.cameraId == cameraId) {
                val clearedError = if (current.lastError == FlashError.TorchUnavailable ||
                    current.lastError == FlashError.CameraInUse
                ) {
                    null
                } else {
                    current.lastError
                }
                _flashState.value = current.copy(
                    cameraId = current.cameraId ?: cameraId,
                    isTorchOn = enabled,
                    isTorchAvailable = true,
                    lastError = clearedError,
                )
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            val current = _flashState.value
            if (current.cameraId == null || current.cameraId == cameraId) {
                _flashState.value = current.copy(
                    isTorchOn = false,
                    isTorchAvailable = false,
                    lastError = FlashError.TorchUnavailable,
                )
            }
        }
    }

    init {
        refreshHardwareState()
        registerTorchCallbackIfNeeded()
    }

    fun refreshHardwareState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _flashState.value = FlashState(
                hasFlashHardware = false,
                isTorchAvailable = false,
                lastError = FlashError.UnsupportedAndroidVersion,
            )
            return
        }

        val manager = cameraManager
        if (manager == null) {
            _flashState.value = FlashState(
                hasFlashHardware = false,
                isTorchAvailable = false,
                lastError = FlashError.NoFlashHardware,
            )
            return
        }

        val resolvedCameraId = findFlashCameraId(manager)
        val systemReportsFlash = appContext.packageManager.hasSystemFeature(
            PackageManager.FEATURE_CAMERA_FLASH,
        )
        val hasFlash = resolvedCameraId != null && (systemReportsFlash || resolvedCameraId.isNotEmpty())

        val current = _flashState.value
        _flashState.value = current.copy(
            hasFlashHardware = hasFlash,
            cameraId = resolvedCameraId,
            isTorchAvailable = hasFlash && current.isTorchAvailable,
            lastError = when {
                !hasFlash -> FlashError.NoFlashHardware
                current.lastError == FlashError.NoFlashHardware -> null
                else -> current.lastError
            },
        )
    }

    private fun registerTorchCallbackIfNeeded() {
        if (torchCallbackRegistered || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val manager = cameraManager ?: return
        try {
            manager.registerTorchCallback(torchCallback, mainHandler)
            torchCallbackRegistered = true
        } catch (_: Exception) {
            // Gracefully continue without live torch callback if unavailable
        }
    }

    private fun findFlashCameraId(manager: CameraManager): String? {
        return try {
            var fallbackId: String? = null
            for (id in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (!hasFlash) continue

                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
                if (fallbackId == null) {
                    fallbackId = id
                }
            }
            fallbackId
        } catch (_: CameraAccessException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun turnOn(): Boolean {
        return setTorchMode(enabled = true)
    }

    @Synchronized
    fun turnOff(): Boolean {
        return setTorchMode(enabled = false)
    }

    @Synchronized
    fun toggle(): Boolean {
        return setTorchMode(enabled = !_flashState.value.isTorchOn)
    }

    @Synchronized
    fun setTorchMode(enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _flashState.value = _flashState.value.copy(
                isTorchOn = false,
                lastError = FlashError.UnsupportedAndroidVersion,
            )
            return false
        }

        if (!PermissionManager.hasCameraPermission(appContext)) {
            _flashState.value = _flashState.value.copy(
                isTorchOn = false,
                lastError = FlashError.CameraPermissionDenied,
            )
            return false
        }

        val manager = cameraManager
        var targetCameraId = _flashState.value.cameraId
        if (manager != null && targetCameraId == null) {
            refreshHardwareState()
            targetCameraId = _flashState.value.cameraId
        }

        if (manager == null || targetCameraId == null) {
            _flashState.value = _flashState.value.copy(
                hasFlashHardware = false,
                isTorchOn = false,
                isTorchAvailable = false,
                lastError = FlashError.NoFlashHardware,
            )
            return false
        }

        return try {
            manager.setTorchMode(targetCameraId, enabled)
            _flashState.value = _flashState.value.copy(
                isTorchOn = enabled,
                isTorchAvailable = true,
                lastError = null,
            )
            true
        } catch (e: CameraAccessException) {
            val mappedError = when (e.reason) {
                CameraAccessException.CAMERA_IN_USE,
                CameraAccessException.MAX_CAMERAS_IN_USE,
                -> FlashError.CameraInUse
                CameraAccessException.CAMERA_DISABLED,
                CameraAccessException.CAMERA_DISCONNECTED,
                CameraAccessException.CAMERA_ERROR,
                -> FlashError.TorchUnavailable
                else -> FlashError.UnknownError(e.localizedMessage ?: "Camera access error")
            }
            _flashState.value = _flashState.value.copy(
                isTorchOn = false,
                isTorchAvailable = mappedError != FlashError.TorchUnavailable,
                lastError = mappedError,
            )
            false
        } catch (_: SecurityException) {
            _flashState.value = _flashState.value.copy(
                isTorchOn = false,
                lastError = FlashError.CameraPermissionDenied,
            )
            false
        } catch (e: IllegalArgumentException) {
            _flashState.value = _flashState.value.copy(
                isTorchOn = false,
                lastError = FlashError.UnknownError(e.localizedMessage ?: "Invalid camera ID"),
            )
            false
        } catch (e: Exception) {
            _flashState.value = _flashState.value.copy(
                isTorchOn = false,
                lastError = FlashError.UnknownError(e.localizedMessage ?: "Unexpected error"),
            )
            false
        }
    }

    fun testFlashlight(scope: CoroutineScope, onTestFinished: (Boolean) -> Unit) {
        testPulseJob?.cancel()
        testPulseJob = scope.launch {
            _flashState.value = _flashState.value.copy(isTesting = true)
            var succeeded = false
            try {
                if (turnOn()) {
                    succeeded = true
                    delay(TEST_PULSE_DURATION_MS)
                    turnOff()
                }
            } finally {
                _flashState.value = _flashState.value.copy(isTesting = false)
                onTestFinished(succeeded)
            }
        }
    }

    fun clearError() {
        _flashState.value = _flashState.value.copy(lastError = null)
    }

    companion object {
        private const val TEST_PULSE_DURATION_MS = 650L

        @Volatile
        private var instance: FlashController? = null

        fun getInstance(context: Context): FlashController {
            return instance ?: synchronized(this) {
                instance ?: FlashController(context).also { instance = it }
            }
        }
    }
}
