package com.example

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.DoubleTapFlashlightTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DoubleTapFlashlightTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                DoubleTapFlashlightScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                )
            }
        }
    }
}

@Composable
fun DoubleTapFlashlightScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasRequestedCameraOnce by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
    }

    val launchAccessibilitySettings = {
        if (!PermissionManager.openAccessibilitySettings(context)) {
            viewModel.onSettingsLaunchFailed()
        }
    }

    val requestCameraOrOpenAppSettings = {
        val activity = context as? ComponentActivity
        val shouldShowRationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        if (!hasRequestedCameraOnce || shouldShowRationale) {
            hasRequestedCameraOnce = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            if (!PermissionManager.openAppDetailsSettings(context)) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TopHeader(onOpenSettings = onOpenSettings)

                ErrorOrFeedbackBanner(
                    flashError = uiState.flashState.lastError,
                    statusFeedbackResId = uiState.statusFeedbackResId,
                    onDismiss = viewModel::dismissError,
                    onGrantCamera = requestCameraOrOpenAppSettings,
                )

                FlashlightHeroCard(
                    flashState = uiState.flashState,
                    onToggle = {
                        if (!uiState.hasCameraPermission) {
                            requestCameraOrOpenAppSettings()
                        } else {
                            viewModel.onIndicatorToggled()
                        }
                    },
                )

                ManualControlRow(
                    isTorchOn = uiState.flashState.isTorchOn,
                    hasFlashHardware = uiState.flashState.hasFlashHardware,
                    isTesting = uiState.flashState.isTesting,
                    onTurnOn = {
                        if (!uiState.hasCameraPermission) {
                            requestCameraOrOpenAppSettings()
                        } else {
                            viewModel.onTurnOnClicked()
                        }
                    },
                    onTurnOff = {
                        if (!uiState.hasCameraPermission) {
                            requestCameraOrOpenAppSettings()
                        } else {
                            viewModel.onTurnOffClicked()
                        }
                    },
                    onTest = {
                        if (!uiState.hasCameraPermission) {
                            requestCameraOrOpenAppSettings()
                        } else {
                            viewModel.onTestFlashlightClicked()
                        }
                    },
                )

                StatusAndPermissionsSection(
                    hasCameraPermission = uiState.hasCameraPermission,
                    accessibilityStatus = uiState.accessibilityStatus,
                    onRequestCamera = requestCameraOrOpenAppSettings,
                    onEnableAccessibility = {
                        viewModel.onEnableAccessibilityClicked(launchAccessibilitySettings)
                    },
                )

                VolumeShortcutsCard(
                    doubleTapWindowMs = uiState.userSettings.doubleTapWindowMs,
                    onOpenSettings = onOpenSettings,
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (uiState.showConsentDialog) {
        AccessibilityConsentDialog(
            onDismiss = viewModel::onDismissConsentDialog,
            onConfirm = { viewModel.onConsentConfirmed(launchAccessibilitySettings) },
        )
    }
}

@Composable
private fun TopHeader(
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ErrorOrFeedbackBanner(
    flashError: FlashError?,
    statusFeedbackResId: Int?,
    onDismiss: () -> Unit,
    onGrantCamera: () -> Unit,
) {
    val bannerMessage: String? = when (flashError) {
        FlashError.NoFlashHardware -> stringResource(R.string.error_no_flash_hardware)
        FlashError.CameraPermissionDenied -> stringResource(R.string.error_camera_permission_denied)
        FlashError.CameraInUse -> stringResource(R.string.error_camera_in_use)
        FlashError.TorchUnavailable -> stringResource(R.string.error_torch_unavailable)
        FlashError.UnsupportedAndroidVersion -> stringResource(R.string.error_unsupported_android)
        is FlashError.UnknownError -> stringResource(R.string.error_camera_generic, flashError.detail)
        null -> statusFeedbackResId?.let { stringResource(it) }
    }

    val isInfoOnly = flashError == null && statusFeedbackResId == R.string.info_test_completed
    val containerColor = if (isInfoOnly) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isInfoOnly) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    AnimatedVisibility(
        visible = bannerMessage != null,
        enter = fadeIn(tween(200)) + expandVertically(),
        exit = fadeOut(tween(150)) + shrinkVertically(),
    ) {
        if (bannerMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_feedback_banner"),
                shape = MaterialTheme.shapes.medium,
                color = containerColor,
                border = BorderStroke(1.dp, contentColor.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isInfoOnly) Icons.Outlined.Info else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bannerMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (flashError == FlashError.CameraPermissionDenied) {
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = onGrantCamera,
                                modifier = Modifier.testTag("banner_grant_camera_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_grant_camera),
                                    color = contentColor,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dismiss_banner_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashlightHeroCard(
    flashState: FlashState,
    onToggle: () -> Unit,
) {
    val isOn = flashState.isTorchOn
    val glowAlpha by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "torch_glow_alpha",
    )
    val circleColor by animateColorAsState(
        targetValue = when {
            !flashState.hasFlashHardware -> MaterialTheme.colorScheme.surfaceVariant
            isOn -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(),
        label = "torch_circle_color",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            !flashState.hasFlashHardware -> MaterialTheme.colorScheme.onSurfaceVariant
            isOn -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "torch_icon_tint",
    )

    val statusText = when {
        !flashState.hasFlashHardware -> stringResource(R.string.flashlight_status_no_hardware)
        !flashState.isTorchAvailable -> stringResource(R.string.flashlight_status_unavailable)
        isOn -> stringResource(R.string.flashlight_status_on)
        else -> stringResource(R.string.flashlight_status_off)
    }

    val statusBadgeColor = when {
        !flashState.hasFlashHardware || !flashState.isTorchAvailable ->
            MaterialTheme.colorScheme.error
        isOn -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor = if (isOn) {
        primaryColor.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("flashlight_hero_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (glowAlpha > 0.01f) {
                Canvas(
                    modifier = Modifier
                        .size(210.dp)
                        .align(Alignment.Center),
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.36f * glowAlpha),
                                primaryColor.copy(alpha = 0.10f * glowAlpha),
                                primaryColor.copy(alpha = 0f),
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension / 2f,
                        ),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = flashState.hasFlashHardware,
                            role = Role.Switch,
                            onClick = onToggle,
                        )
                        .testTag("flashlight_indicator_button"),
                    shape = CircleShape,
                    color = circleColor,
                    border = BorderStroke(
                        width = if (isOn) 2.dp else 1.dp,
                        color = if (isOn) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isOn) {
                                Icons.Rounded.FlashlightOn
                            } else {
                                Icons.Rounded.FlashlightOff
                            },
                            contentDescription = statusText,
                            tint = iconTint,
                            modifier = Modifier.size(54.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusBadgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusBadgeColor.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusBadgeColor,
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("flashlight_status_badge"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualControlRow(
    isTorchOn: Boolean,
    hasFlashHardware: Boolean,
    isTesting: Boolean,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onTurnOn,
                enabled = hasFlashHardware && !isTesting,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .testTag("manual_on_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FlashlightOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_turn_on),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            FilledTonalButton(
                onClick = onTurnOff,
                enabled = hasFlashHardware && !isTesting,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .testTag("manual_off_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isTorchOn) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (isTorchOn) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_turn_off),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        OutlinedButton(
            onClick = onTest,
            enabled = hasFlashHardware && !isTesting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .testTag("test_flashlight_button"),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_test_flashlight),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatusAndPermissionsSection(
    hasCameraPermission: Boolean,
    accessibilityStatus: AccessibilityStatus,
    onRequestCamera: () -> Unit,
    onEnableAccessibility: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionStatusCard(
            icon = Icons.Outlined.CameraAlt,
            title = stringResource(R.string.permission_camera_title),
            statusLabel = if (hasCameraPermission) {
                stringResource(R.string.permission_camera_granted)
            } else {
                stringResource(R.string.permission_camera_denied)
            },
            isHealthy = hasCameraPermission,
            description = stringResource(R.string.permission_camera_rationale),
            actionLabel = if (!hasCameraPermission) stringResource(R.string.action_grant_camera) else null,
            buttonTestTag = "grant_camera_button",
            cardTestTag = "camera_status_card",
            onAction = onRequestCamera,
        )

        val (serviceLabel, serviceHealthy) = when (accessibilityStatus) {
            AccessibilityStatus.ACTIVE -> stringResource(R.string.service_status_enabled) to true
            AccessibilityStatus.PAUSED -> stringResource(R.string.service_status_paused) to false
            AccessibilityStatus.INTERRUPTED -> stringResource(R.string.service_status_interrupted) to false
            AccessibilityStatus.DISABLED -> stringResource(R.string.service_status_disabled) to false
        }

        PermissionStatusCard(
            icon = Icons.Rounded.AccessibilityNew,
            title = stringResource(R.string.service_status_title),
            statusLabel = serviceLabel,
            isHealthy = serviceHealthy,
            description = stringResource(R.string.accessibility_service_summary),
            actionLabel = if (accessibilityStatus != AccessibilityStatus.ACTIVE) {
                stringResource(R.string.action_enable_accessibility)
            } else {
                null
            },
            buttonTestTag = "enable_accessibility_button",
            cardTestTag = "accessibility_status_card",
            onAction = onEnableAccessibility,
        )
    }
}

@Composable
private fun PermissionStatusCard(
    icon: ImageVector,
    title: String,
    statusLabel: String,
    isHealthy: Boolean,
    description: String,
    actionLabel: String?,
    buttonTestTag: String,
    cardTestTag: String,
    onAction: () -> Unit,
) {
    val badgeColor = if (isHealthy) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTestTag),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isHealthy) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isHealthy) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (actionLabel != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(buttonTestTag),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeShortcutsCard(
    doubleTapWindowMs: Int,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("volume_shortcuts_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.shortcuts_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenSettings),
                ) {
                    Text(
                        text = stringResource(R.string.settings_timing_value_ms, doubleTapWindowMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            ShortcutInstructionRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                keyBadge = "VOL + ×2",
                title = stringResource(R.string.shortcut_vol_up_title),
                subtitle = stringResource(R.string.shortcut_vol_up_desc),
                isOnShortcut = true,
            )

            ShortcutInstructionRow(
                icon = Icons.Rounded.VolumeDown,
                keyBadge = "VOL - ×2",
                title = stringResource(R.string.shortcut_vol_down_title),
                subtitle = stringResource(R.string.shortcut_vol_down_desc),
                isOnShortcut = false,
            )

            Text(
                text = stringResource(R.string.shortcut_normal_volume_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShortcutInstructionRow(
    icon: ImageVector,
    keyBadge: String,
    title: String,
    subtitle: String,
    isOnShortcut: Boolean,
) {
    val accent = if (isOnShortcut) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        ) {
            Text(
                text = keyBadge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun AccessibilityConsentDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var isChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.consent_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.consent_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isChecked = !isChecked }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                        modifier = Modifier.testTag("consent_checkbox"),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.consent_checkbox_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isChecked,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("consent_continue_button"),
            ) {
                Text(stringResource(R.string.action_continue_to_settings))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("consent_cancel_button"),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
