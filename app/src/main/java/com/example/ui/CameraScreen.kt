package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicExternalOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Portrait
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.camera.CameraViewModel
import com.example.camera.model.CameraTab
import com.example.camera.model.CameraUiState
import com.example.camera.model.RecordingState
import com.example.camera.model.TorchMode
import com.example.ui.theme.AmberPro
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.HudBackdrop
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudOnDark
import com.example.ui.theme.HudOnDarkMuted
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.HudSurfaceSubtle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasAudioPermission = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                ),
            )
        }
    }

    LaunchedEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            viewModel.attachLifecycleAndBind(lifecycleOwner)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasCameraPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    hasAudioPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCameraPermission) {
                        viewModel.onAppForegrounded()
                    }
                }

                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    viewModel.onAppBackgrounded()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAppBackgrounded()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var showEffectsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var pendingResetType by remember { mutableStateOf<ResetType?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { _ ->
        if (!hasCameraPermission) {
            PermissionGate(
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                        ),
                    )
                },
            )
        } else {
            CameraViewfinderInterface(
                uiState = uiState,
                surfaceRequest = surfaceRequest,
                onTabSelect = viewModel::selectTab,
                onSwitchLens = viewModel::switchCameraLens,
                onCycleTorch = viewModel::cycleTorchMode,
                onOpenEffects = { showEffectsSheet = true },
                onOpenSettings = { showSettingsSheet = true },
                onRecordClick = { viewModel.toggleVideoRecording(hasAudioPermission) },
                onPauseResumeClick = viewModel::togglePauseResumeRecording,
                onPhotoCaptureClick = viewModel::capturePhoto,
                onZoomRatioSelect = viewModel::setZoomRatio,
                onTapToFocus = viewModel::tapToFocus,
                onOpenLastCapture = { uriString ->
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                Uri.parse(uriString),
                                if (uiState.lastCaptureIsVideo) "video/*" else "image/*",
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                },
            )
        }
    }

    if (showEffectsSheet) {
        EffectsBottomSheet(
            uiState = uiState,
            onDismiss = { showEffectsSheet = false },
            onPortraitToggle = viewModel::setPortraitEnabled,
            onBackgroundBlurChange = viewModel::setBackgroundBlur,
            onBeautyChange = viewModel::setBeautyStrength,
            onColorFilterSelect = viewModel::setColorFilter,
            onRequestResetEffects = { pendingResetType = ResetType.EFFECTS },
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            uiState = uiState,
            onDismiss = { showSettingsSheet = false },
            onFixMirrorChange = viewModel::setFixMirrorEnabled,
            onBitrateSelect = viewModel::setVideoBitrateMbps,
            onQualitySelect = viewModel::setVideoQualityKey,
            onAudioDeviceSelect = viewModel::setAudioDeviceId,
            onTorchModeSelect = viewModel::setTorchMode,
            onRequestResetCameraSettings = { pendingResetType = ResetType.CAMERA_SETTINGS },
        )
    }

    pendingResetType?.let { resetType ->
        val title = when (resetType) {
            ResetType.EFFECTS -> stringResource(R.string.reset_effects)
            ResetType.CAMERA_SETTINGS -> stringResource(R.string.reset_camera_settings)
        }
        ResetConfirmationDialog(
            title = title,
            onDismiss = { pendingResetType = null },
            onConfirmReset = {
                when (resetType) {
                    ResetType.EFFECTS -> viewModel.resetEffectsSettings()
                    ResetType.CAMERA_SETTINGS -> viewModel.resetCameraSettings()
                }
                pendingResetType = null
            },
        )
    }
}

private enum class ResetType {
    EFFECTS,
    CAMERA_SETTINGS,
}

@Composable
private fun PermissionGate(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.permission_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("grant_permissions_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.grant_permissions))
                }
            }
        }
    }
}

@Composable
private fun CameraViewfinderInterface(
    uiState: CameraUiState,
    surfaceRequest: SurfaceRequest?,
    onTabSelect: (CameraTab) -> Unit,
    onSwitchLens: () -> Unit,
    onCycleTorch: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenSettings: () -> Unit,
    onRecordClick: () -> Unit,
    onPauseResumeClick: () -> Unit,
    onPhotoCaptureClick: () -> Unit,
    onZoomRatioSelect: (Float) -> Unit,
    onTapToFocus: (normX: Float, normY: Float, previewNormX: Float, previewNormY: Float) -> Unit,
    onOpenLastCapture: (String) -> Unit,
) {
    val coordinateTransformer = remember { MutableCoordinateTransformer() }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var lensFlipRotation by remember { mutableFloatStateOf(0f) }
    val animatedFlipRotation by animateFloatAsState(
        targetValue = lensFlipRotation,
        animationSpec = spring(stiffness = 380f),
        label = "lens_flip_anim",
    )

    val isRecording = uiState.recordingState is RecordingState.Recording
    val recordingState = uiState.recordingState as? RecordingState.Recording

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Live Full-Screen CameraX Viewfinder
        surfaceRequest?.let { req ->
            CameraXViewfinder(
                surfaceRequest = req,
                coordinateTransformer = coordinateTransformer,
                implementationMode = ImplementationMode.EXTERNAL,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { previewSize = it }
                    .pointerInput(req) {
                        detectTapGestures { tapOffset ->
                            val bufferWidth = req.resolution.width.toFloat().coerceAtLeast(1f)
                            val bufferHeight = req.resolution.height.toFloat().coerceAtLeast(1f)
                            val sensorPoint = with(coordinateTransformer) {
                                tapOffset.transform()
                            }
                            val normX = (sensorPoint.x / bufferWidth).coerceIn(0f, 1f)
                            val normY = (sensorPoint.y / bufferHeight).coerceIn(0f, 1f)
                            val prevX = if (previewSize.width > 0) {
                                (tapOffset.x / previewSize.width).coerceIn(0f, 1f)
                            } else {
                                0.5f
                            }
                            val prevY = if (previewSize.height > 0) {
                                (tapOffset.y / previewSize.height).coerceIn(0f, 1f)
                            } else {
                                0.5f
                            }
                            onTapToFocus(normX, normY, prevX, prevY)
                        }
                    }
                    .pointerInput(uiState.lensCapabilities.minZoomRatio, uiState.lensCapabilities.maxZoomRatio) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (zoomChange != 1f) {
                                onZoomRatioSelect(uiState.currentZoomRatio * zoomChange)
                            }
                        }
                    },
            )
        }

        // Subtle shutter flash overlay on photo capture
        AnimatedVisibility(
            visible = uiState.isPhotoCapturing,
            enter = fadeIn(tween(40)),
            exit = fadeOut(tween(140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.28f)),
            )
        }

        // Focus Reticle Overlay
        uiState.focusPointNormalized?.let { (nx, ny) ->
            if (previewSize.width > 0 && previewSize.height > 0) {
                val reticleSize = 68.dp
                val halfPx = with(LocalDensity.current) { (reticleSize / 2).roundToPx() }
                val xPx = (nx * previewSize.width).roundToInt() - halfPx
                val yPx = (ny * previewSize.height).roundToInt() - halfPx
                Box(
                    modifier = Modifier
                        .offset { IntOffset(xPx, yPx) }
                        .size(reticleSize),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = AmberPro,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                        drawCircle(
                            color = AmberPro.copy(alpha = 0.25f),
                            radius = size.minDimension * 0.14f,
                        )
                    }
                }
            }
        }

        // Top & Bottom Subtle Gradients for crisp HUD readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                    ),
                ),
        )

        // 2. Top Floating Camera HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Torch Pill
                TorchHudPill(
                    torchMode = uiState.cameraSettings.torchMode,
                    isTorchOn = uiState.isTorchActuallyOn,
                    hasFlashUnit = uiState.lensCapabilities.hasFlashUnit,
                    onClick = onCycleTorch,
                )

                // Hardware Resolution / Codec / FPS / Bitrate Pill
                val resText = uiState.activeResolution?.label ?: "AUTO"
                Surface(
                    shape = CircleShape,
                    color = HudBackdrop,
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings)
                        .testTag("spec_info_pill"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = resText,
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberPro,
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudOnDarkMuted,
                        )
                        Text(
                            text = "30FPS",
                            style = MaterialTheme.typography.labelMedium,
                            color = HudOnDark,
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudOnDarkMuted,
                        )
                        Text(
                            text = "${uiState.effectiveBitrateMbps}M",
                            style = MaterialTheme.typography.labelMedium,
                            color = HudOnDark,
                        )
                    }
                }

                // Settings Gear Button
                Surface(
                    shape = CircleShape,
                    color = HudBackdrop,
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier.size(48.dp),
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                            tint = HudOnDark,
                        )
                    }
                }
            }

            // Secondary Status Row: Audio Source + Portrait/Codec Badge or Live Recording Timer
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recordingState != null) {
                    RecordingTimerBadge(
                        elapsedMs = recordingState.elapsedMs,
                        isPaused = recordingState.isPaused,
                    )
                } else {
                    // Audio Source Status Chip
                    val activeMic = uiState.activeAudioDevice
                    Surface(
                        shape = CircleShape,
                        color = HudSurfaceSubtle,
                        border = BorderStroke(1.dp, HudBorder),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSettings)
                            .testTag("audio_chip"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = if (activeMic.isExternal) {
                                    Icons.Outlined.MicExternalOn
                                } else {
                                    Icons.Outlined.Mic
                                },
                                contentDescription = null,
                                tint = if (activeMic.isExternal) EmeraldActive else HudOnDarkMuted,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = activeMic.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = HudOnDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // H.265 Codec Badge
                    Surface(
                        shape = CircleShape,
                        color = HudSurfaceSubtle,
                        border = BorderStroke(1.dp, HudBorder),
                    ) {
                        Text(
                            text = if (uiState.encoderCapabilities.hevcSupported) "H.265" else "H.264",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudOnDarkMuted,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }

                    if (uiState.effectsSettings.portraitEnabled) {
                        Surface(
                            shape = CircleShape,
                            color = AmberPro.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, AmberPro.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onOpenEffects),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Portrait,
                                    contentDescription = null,
                                    tint = AmberPro,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "PORTRAIT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberPro,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Camera Error Banner (if hardware unavailable)
        uiState.cameraError?.let { errorMsg ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 3. Bottom Control Deck (Zoom, Camera Switch, Mode Tabs, Capture Controls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Zoom Preset Strip + Dedicated Camera Switch Button (always visible in BOTH VIDEO & PHOTO!)
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Zoom presets centered
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(HudBackdrop)
                        .border(1.dp, HudBorder, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    uiState.lensCapabilities.zoomPresets.forEach { preset ->
                        val isClosest = kotlin.math.abs(uiState.currentZoomRatio - preset) < 0.25f
                        val label = if (preset < 1f) {
                            String.format(Locale.US, "%.1fx", preset)
                        } else {
                            "${preset.roundToInt()}x"
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (isClosest) AmberPro else Color.Transparent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onZoomRatioSelect(preset) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isClosest) Color.Black else HudOnDark,
                                fontWeight = if (isClosest) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }

                // Dedicated Front/Back Camera Switch Button — clearly visible in BOTH VIDEO & PHOTO modes
                Surface(
                    shape = CircleShape,
                    color = HudSurfaceElevated,
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .alpha(if (isRecording) 0.45f else 1f),
                ) {
                    IconButton(
                        onClick = {
                            if (!isRecording) {
                                lensFlipRotation += 180f
                            }
                            onSwitchLens()
                        },
                        modifier = Modifier.testTag("switch_camera_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = stringResource(R.string.action_switch_camera),
                            tint = HudOnDark,
                            modifier = Modifier.rotate(animatedFlipRotation),
                        )
                    }
                }
            }

            // Two Main Tabs: VIDEO (Default) and PHOTO
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(HudBackdrop)
                    .border(1.dp, HudBorder, CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CameraTabPill(
                    label = stringResource(R.string.tab_video),
                    selected = uiState.activeTab == CameraTab.VIDEO,
                    enabled = !isRecording,
                    testTag = "tab_video",
                    onClick = { onTabSelect(CameraTab.VIDEO) },
                )
                CameraTabPill(
                    label = stringResource(R.string.tab_photo),
                    selected = uiState.activeTab == CameraTab.PHOTO,
                    enabled = !isRecording,
                    testTag = "tab_photo",
                    onClick = { onTabSelect(CameraTab.PHOTO) },
                )
            }

            // Primary Shutter Row: Effects (Left) — Main Shutter (Center) — Photo Snapshot / Thumbnail (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // LEFT: Effects Button
                EffectsTriggerButton(
                    activeCount = uiState.effectsSettings.activeEffectCount,
                    onClick = onOpenEffects,
                )

                // CENTER: Record Button (VIDEO) or Photo Shutter Button (PHOTO)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.activeTab == CameraTab.VIDEO) {
                        AnimatedVisibility(visible = isRecording) {
                            Surface(
                                shape = CircleShape,
                                color = HudSurfaceElevated,
                                border = BorderStroke(1.dp, HudBorder),
                                modifier = Modifier.size(48.dp),
                            ) {
                                IconButton(
                                    onClick = onPauseResumeClick,
                                    modifier = Modifier.testTag("pause_resume_button"),
                                ) {
                                    Icon(
                                        imageVector = if (recordingState?.isPaused == true) {
                                            Icons.Rounded.PlayArrow
                                        } else {
                                            Icons.Rounded.Pause
                                        },
                                        contentDescription = stringResource(
                                            if (recordingState?.isPaused == true) {
                                                R.string.action_resume_recording
                                            } else {
                                                R.string.action_pause_recording
                                            },
                                        ),
                                        tint = HudOnDark,
                                    )
                                }
                            }
                        }

                        VideoRecordShutterButton(
                            isRecording = isRecording,
                            isFinalizing = uiState.recordingState is RecordingState.Finalizing,
                            onClick = onRecordClick,
                        )
                    } else {
                        PhotoShutterButton(
                            isCapturing = uiState.isPhotoCapturing,
                            onClick = onPhotoCaptureClick,
                        )
                    }
                }

                // RIGHT: Separate Photo Capture Button in VIDEO mode (always visible, works during recording!)
                // OR Last Capture Thumbnail in PHOTO mode
                if (uiState.activeTab == CameraTab.VIDEO) {
                    val simultaneousSupported = uiState.lensCapabilities.simultaneousPhotoInVideoSupported
                    Surface(
                        shape = CircleShape,
                        color = HudSurfaceElevated,
                        border = BorderStroke(
                            1.5.dp,
                            if (simultaneousSupported) Color.White.copy(alpha = 0.85f) else HudBorder,
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .alpha(if (simultaneousSupported) 1f else 0.45f),
                    ) {
                        IconButton(
                            onClick = onPhotoCaptureClick,
                            modifier = Modifier.testTag("video_snapshot_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = stringResource(R.string.action_capture_photo),
                                tint = HudOnDark,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                } else {
                    LastCaptureThumbnailButton(
                        lastCapturedUri = uiState.lastCapturedUri,
                        onClick = {
                            uiState.lastCapturedUri?.let(onOpenLastCapture)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TorchHudPill(
    torchMode: TorchMode,
    isTorchOn: Boolean,
    hasFlashUnit: Boolean,
    onClick: () -> Unit,
) {
    val label = when (torchMode) {
        TorchMode.OFF -> stringResource(R.string.torch_off)
        TorchMode.AUTO -> stringResource(R.string.torch_auto)
        TorchMode.ALWAYS_ON -> "ON"
    }
    val icon = when (torchMode) {
        TorchMode.OFF -> Icons.Rounded.FlashOff
        TorchMode.AUTO -> Icons.Rounded.FlashAuto
        TorchMode.ALWAYS_ON -> Icons.Rounded.FlashOn
    }
    val activeHighlight = torchMode != TorchMode.OFF && hasFlashUnit

    Surface(
        shape = CircleShape,
        color = if (isTorchOn) AmberPro.copy(alpha = 0.25f) else HudBackdrop,
        border = BorderStroke(
            1.dp,
            if (isTorchOn) AmberPro else HudBorder,
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag("torch_button"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.action_torch),
                tint = if (activeHighlight) AmberPro else HudOnDark,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (activeHighlight) AmberPro else HudOnDark,
            )
        }
    }
}

@Composable
private fun RecordingTimerBadge(
    elapsedMs: Long,
    isPaused: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec_dot_alpha",
    )

    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    val formatted = String.format(Locale.US, "%02d:%02d", mins, secs)

    Surface(
        shape = CircleShape,
        color = CrimsonRecord.copy(alpha = 0.88f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isPaused) 0.5f else pulseAlpha)),
            )
            Text(
                text = if (isPaused) "PAUSED $formatted" else formatted,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CameraTabPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) AmberPro else Color.Transparent,
        label = "tab_bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.Black else HudOnDark,
        label = "tab_fg",
    )

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EffectsTriggerButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            shape = CircleShape,
            color = if (activeCount > 0) AmberPro.copy(alpha = 0.24f) else HudSurfaceElevated,
            border = BorderStroke(
                1.5.dp,
                if (activeCount > 0) AmberPro else HudBorder,
            ),
            modifier = Modifier.size(54.dp),
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.testTag("effects_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = stringResource(R.string.action_effects),
                    tint = if (activeCount > 0) AmberPro else HudOnDark,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (activeCount > 0) {
            Surface(
                shape = CircleShape,
                color = AmberPro,
                modifier = Modifier.size(18.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoRecordShutterButton(
    isRecording: Boolean,
    isFinalizing: Boolean,
    onClick: () -> Unit,
) {
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) 28.dp else 56.dp,
        animationSpec = spring(stiffness = 420f),
        label = "record_inner_size",
    )
    val innerCorner by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 28.dp,
        animationSpec = spring(stiffness = 420f),
        label = "record_inner_corner",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(3.5.dp, Color.White, CircleShape)
            .clickable(enabled = !isFinalizing, onClick = onClick)
            .testTag("record_button"),
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(CrimsonRecord),
        )
    }
}

@Composable
private fun PhotoShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit,
) {
    val innerScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.86f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "photo_shutter_scale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(3.5.dp, Color.White, CircleShape)
            .clickable(enabled = !isCapturing, onClick = onClick)
            .testTag("photo_capture_button"),
    ) {
        Box(
            modifier = Modifier
                .size((58 * innerScale).dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun LastCaptureThumbnailButton(
    lastCapturedUri: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, lastCapturedUri) {
        value = if (lastCapturedUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(
                        Uri.parse(lastCapturedUri),
                        Size(160, 160),
                        null,
                    )
                }.getOrNull()
            }
        } else {
            null
        }
    }

    Surface(
        shape = CircleShape,
        color = HudSurfaceElevated,
        border = BorderStroke(1.5.dp, HudBorder),
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(enabled = lastCapturedUri != null, onClick = onClick)
            .testTag("gallery_thumbnail_button"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val bmp = thumbnail
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_last_capture),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = stringResource(R.string.cd_last_capture),
                    tint = if (lastCapturedUri != null) HudOnDark else HudOnDarkMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
