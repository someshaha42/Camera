package com.example.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camera.effects.GlEffectsSurfaceProcessor
import com.example.camera.hardware.AudioInputMonitor
import com.example.camera.hardware.VideoCapabilityDetector
import com.example.camera.model.CameraSettings
import com.example.camera.model.CameraTab
import com.example.camera.model.EffectsSettings
import com.example.camera.model.EncoderCodecCapabilities
import com.example.camera.model.LensFacing
import com.example.camera.model.LensHardwareCapabilities
import com.example.camera.model.RecordingState
import com.example.camera.model.TorchMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Pure AndroidX CameraX controller managing Preview, VideoCapture (H.265 / 30 FPS),
 * ImageCapture (simultaneous snapshot during recording + dedicated photo mode),
 * ExtensionsManager (Hardware Bokeh Portrait), GlEffectsSurfaceProcessor, Torch, Focus & Zoom.
 */
class CameraController(
    private val context: Context,
    private val audioInputMonitor: AudioInputMonitor,
) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var boundCamera: Camera? = null

    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null
    private var videoCaptureUseCase: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val glProcessor = GlEffectsSurfaceProcessor()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _isTorchActuallyOn = MutableStateFlow(false)
    val isTorchActuallyOn: StateFlow<Boolean> = _isTorchActuallyOn.asStateFlow()

    private val _isAutoTorchDarkScene = MutableStateFlow(false)
    val isAutoTorchDarkScene: StateFlow<Boolean> = _isAutoTorchDarkScene.asStateFlow()

    private val _currentZoomRatio = MutableStateFlow(1f)
    val currentZoomRatio: StateFlow<Float> = _currentZoomRatio.asStateFlow()

    private var currentTab: CameraTab = CameraTab.VIDEO
    private var currentLens: LensFacing = LensFacing.BACK
    private var currentCameraSettings: CameraSettings = CameraSettings()
    private var currentEffectsSettings: EffectsSettings = EffectsSettings()
    private var currentEncoderCaps: EncoderCodecCapabilities =
        VideoCapabilityDetector.queryEncoderCapabilities()
    private var currentDeviceRotation: Int = Surface.ROTATION_0
    private var isHardwarePhotoBokehBound: Boolean = false

    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var lightListenerRegistered = false

    private val lightSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val lux = event.values.firstOrNull() ?: return
            val currentlyDark = _isAutoTorchDarkScene.value
            val nextDark = when {
                !currentlyDark && lux < AUTO_TORCH_DARK_LUX -> true
                currentlyDark && lux > AUTO_TORCH_BRIGHT_LUX -> false
                else -> currentlyDark
            }
            if (nextDark != currentlyDark) {
                _isAutoTorchDarkScene.value = nextDark
                applyTorchState()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val orientationListener = object : OrientationEventListener(appContext) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            if (rotation != currentDeviceRotation) {
                currentDeviceRotation = rotation
                imageCaptureUseCase?.targetRotation = rotation
                videoCaptureUseCase?.targetRotation = rotation
            }
        }
    }

    fun startOrientationMonitoring() {
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    fun stopOrientationMonitoring() {
        orientationListener.disable()
    }

    fun getEncoderCapabilities(): EncoderCodecCapabilities = currentEncoderCaps

    suspend fun ensureInitialized(): Pair<ProcessCameraProvider, ExtensionsManager?> {
        val provider = cameraProvider ?: ProcessCameraProvider.getInstance(appContext).awaitFuture()
            .also { cameraProvider = it }
        val extMgr = extensionsManager ?: runCatching {
            ExtensionsManager.getInstanceAsync(appContext, provider).awaitFuture()
        }.getOrNull().also { extensionsManager = it }
        return provider to extMgr
    }

    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        tab: CameraTab,
        lensFacing: LensFacing,
        cameraSettings: CameraSettings,
        effectsSettings: EffectsSettings,
    ): Result<LensHardwareCapabilities> {
        return runCatching {
            val (provider, extMgr) = ensureInitialized()

            currentTab = tab
            currentLens = lensFacing
            currentCameraSettings = cameraSettings
            currentEffectsSettings = effectsSettings

            val hasBack = runCatching {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            }.getOrDefault(false)
            val hasFront = runCatching {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            }.getOrDefault(false)

            val resolvedLens = when {
                lensFacing == LensFacing.BACK && hasBack -> LensFacing.BACK
                lensFacing == LensFacing.FRONT && hasFront -> LensFacing.FRONT
                hasBack -> LensFacing.BACK
                hasFront -> LensFacing.FRONT
                else -> error("No camera hardware detected on this device")
            }
            currentLens = resolvedLens

            val baseSelector = if (resolvedLens == LensFacing.BACK) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            // Query hardware capabilities via CameraX CameraInfo
            val baseCameraInfo = provider.getCameraInfo(baseSelector)
            val hardwareResolutions = VideoCapabilityDetector.queryHardwareResolutions(baseCameraInfo)
            val maxResolution = hardwareResolutions.firstOrNull()
            val selectedResolution = hardwareResolutions.firstOrNull {
                it.key == cameraSettings.selectedQualityKey
            } ?: maxResolution

            val photoBokehAvailable = extMgr?.let {
                runCatching { it.isExtensionAvailable(baseSelector, ExtensionMode.BOKEH) }
                    .getOrDefault(false)
            } ?: false

            val useHardwarePhotoBokeh = tab == CameraTab.PHOTO &&
                effectsSettings.portraitEnabled &&
                photoBokehAvailable &&
                extMgr != null

            val effectiveSelector = if (useHardwarePhotoBokeh) {
                extMgr!!.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.BOKEH)
            } else {
                baseSelector
            }
            isHardwarePhotoBokehBound = useHardwarePhotoBokeh

            glProcessor.updateEffects(effectsSettings, isPhotoHardwareBokehActive = useHardwarePhotoBokeh)

            val preview = Preview.Builder()
                .setTargetRotation(currentDeviceRotation)
                .build()
                .apply {
                    setSurfaceProvider { request ->
                        _surfaceRequest.value = request
                    }
                }
            previewUseCase = preview

            val flashMode = when (cameraSettings.torchMode) {
                TorchMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                TorchMode.OFF, TorchMode.ALWAYS_ON -> ImageCapture.FLASH_MODE_OFF
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .setTargetRotation(currentDeviceRotation)
                .build()
            imageCaptureUseCase = imageCapture

            var simultaneousPhotoSupported = true
            var realTimeGlSupported = !useHardwarePhotoBokeh

            provider.unbindAll()

            val camera: Camera = if (tab == CameraTab.VIDEO) {
                val targetBitrateBps = resolveSupportedBitrateBps(cameraSettings.selectedBitrateMbps)
                val qualitySelector = if (selectedResolution != null) {
                    QualitySelector.from(
                        selectedResolution.quality,
                        FallbackStrategy.lowerQualityOrHigherThan(selectedResolution.quality),
                    )
                } else {
                    QualitySelector.fromOrderedList(
                        listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                    )
                }

                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .setTargetVideoEncodingBitRate(targetBitrateBps)
                    .build()

                val targetFpsRange = pickTarget30FpsRange(baseCameraInfo)
                val mirrorMode = if (cameraSettings.fixMirrorEnabled) {
                    MirrorMode.MIRROR_MODE_OFF
                } else {
                    MirrorMode.MIRROR_MODE_ON_FRONT_ONLY
                }

                val videoCaptureBuilder = VideoCapture.Builder(recorder)
                    .setDynamicRange(DynamicRange.SDR) // HDR disabled by default
                    .setMirrorMode(mirrorMode)
                    .setTargetRotation(currentDeviceRotation)
                if (targetFpsRange != null) {
                    videoCaptureBuilder.setTargetFrameRate(targetFpsRange)
                }
                val videoCapture = videoCaptureBuilder.build()
                videoCaptureUseCase = videoCapture

                // Prefer binding Preview + VideoCapture + ImageCapture + real-time GL CameraEffect
                // Gracefully degrade if device hardware has fewer concurrent stream combinations.
                val targetsWithPhoto = CameraEffect.PREVIEW or
                    CameraEffect.VIDEO_CAPTURE or
                    CameraEffect.IMAGE_CAPTURE

                bindVideoWithFallback(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    selector = effectiveSelector,
                    preview = preview,
                    videoCapture = videoCapture,
                    imageCapture = imageCapture,
                    targetsWithPhoto = targetsWithPhoto,
                    onResult = { simSupported, glSupported ->
                        simultaneousPhotoSupported = simSupported
                        realTimeGlSupported = glSupported
                    },
                )
            } else {
                videoCaptureUseCase = null
                bindPhotoWithFallback(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    selector = effectiveSelector,
                    preview = preview,
                    imageCapture = imageCapture,
                    allowGlEffect = !useHardwarePhotoBokeh,
                    onResult = { glSupported ->
                        realTimeGlSupported = glSupported
                    },
                )
            }

            boundCamera = camera
            observeTorchAndZoom(lifecycleOwner, camera)
            updateAutoTorchSensorSubscription()
            applyTorchState()

            val zoomState = camera.cameraInfo.zoomState.value
            val minZoom = zoomState?.minZoomRatio ?: 1f
            val maxZoom = zoomState?.maxZoomRatio ?: 1f
            val zoomPresets = buildList {
                if (minZoom < 0.85f) add(0.5f.coerceAtLeast(minZoom))
                add(1f)
                if (maxZoom >= 2f) add(2f)
                if (maxZoom >= 5f) add(5f)
            }

            LensHardwareCapabilities(
                lensFacing = resolvedLens,
                hasBackCamera = hasBack,
                hasFrontCamera = hasFront,
                hasFlashUnit = camera.cameraInfo.hasFlashUnit(),
                supportedResolutions = hardwareResolutions,
                maxResolution = maxResolution,
                photoBokehExtensionSupported = photoBokehAvailable,
                simultaneousPhotoInVideoSupported = simultaneousPhotoSupported,
                realTimeVideoEffectsSupported = realTimeGlSupported,
                minZoomRatio = minZoom,
                maxZoomRatio = maxZoom,
                zoomPresets = zoomPresets,
                activeFpsLabel = "30 FPS",
            )
        }
    }

    private fun bindVideoWithFallback(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        selector: CameraSelector,
        preview: Preview,
        videoCapture: VideoCapture<Recorder>,
        imageCapture: ImageCapture,
        targetsWithPhoto: Int,
        onResult: (simultaneousSupported: Boolean, glSupported: Boolean) -> Unit,
    ): Camera {
        // Attempt 1: Preview + VideoCapture + ImageCapture + GL CameraEffect
        runCatching {
            val effect = glProcessor.createCameraEffect(targetsWithPhoto)
            val group = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(videoCapture)
                .addUseCase(imageCapture)
                .addEffect(effect)
                .build()
            return provider.bindToLifecycle(lifecycleOwner, selector, group).also {
                onResult(true, true)
            }
        }

        // Attempt 2: Preview + VideoCapture + GL CameraEffect (without simultaneous ImageCapture)
        runCatching {
            val effect = glProcessor.createCameraEffect(
                CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            )
            val group = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(videoCapture)
                .addEffect(effect)
                .build()
            return provider.bindToLifecycle(lifecycleOwner, selector, group).also {
                imageCaptureUseCase = null
                onResult(false, true)
            }
        }

        // Attempt 3: Preview + VideoCapture + ImageCapture (without GL CameraEffect)
        runCatching {
            return provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                videoCapture,
                imageCapture,
            ).also {
                onResult(true, false)
            }
        }

        // Attempt 4: Baseline Preview + VideoCapture
        return provider.bindToLifecycle(
            lifecycleOwner,
            selector,
            preview,
            videoCapture,
        ).also {
            imageCaptureUseCase = null
            onResult(false, false)
        }
    }

    private fun bindPhotoWithFallback(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        selector: CameraSelector,
        preview: Preview,
        imageCapture: ImageCapture,
        allowGlEffect: Boolean,
        onResult: (glSupported: Boolean) -> Unit,
    ): Camera {
        if (allowGlEffect) {
            runCatching {
                val effect = glProcessor.createCameraEffect(
                    CameraEffect.PREVIEW or CameraEffect.IMAGE_CAPTURE,
                )
                val group = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageCapture)
                    .addEffect(effect)
                    .build()
                return provider.bindToLifecycle(lifecycleOwner, selector, group).also {
                    onResult(true)
                }
            }
        }
        return provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture).also {
            onResult(false)
        }
    }

    fun updateLiveEffects(effectsSettings: EffectsSettings) {
        currentEffectsSettings = effectsSettings
        glProcessor.updateEffects(
            effectsSettings,
            isPhotoHardwareBokehActive = isHardwarePhotoBokehBound,
        )
    }

    fun updateTorchMode(mode: TorchMode) {
        currentCameraSettings = currentCameraSettings.copy(torchMode = mode)
        imageCaptureUseCase?.flashMode = when (mode) {
            TorchMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            TorchMode.OFF, TorchMode.ALWAYS_ON -> ImageCapture.FLASH_MODE_OFF
        }
        updateAutoTorchSensorSubscription()
        applyTorchState()
    }

    fun turnTorchOffImmediately() {
        boundCamera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                runCatching { cam.cameraControl.enableTorch(false) }
            }
        }
        _isTorchActuallyOn.value = false
    }

    fun applyTorchState() {
        val cam = boundCamera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            _isTorchActuallyOn.value = false
            return
        }
        val shouldEnableTorch = when (currentCameraSettings.torchMode) {
            TorchMode.OFF -> false
            TorchMode.ALWAYS_ON -> true
            TorchMode.AUTO -> _isAutoTorchDarkScene.value
        }
        cam.cameraControl.enableTorch(shouldEnableTorch)
    }

    private fun updateAutoTorchSensorSubscription() {
        val mgr = sensorManager ?: return
        val sensor = lightSensor ?: return
        val shouldListen = currentCameraSettings.torchMode == TorchMode.AUTO
        if (shouldListen && !lightListenerRegistered) {
            mgr.registerListener(lightSensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            lightListenerRegistered = true
        } else if (!shouldListen && lightListenerRegistered) {
            mgr.unregisterListener(lightSensorListener)
            lightListenerRegistered = false
            _isAutoTorchDarkScene.value = false
        }
    }

    private fun observeTorchAndZoom(lifecycleOwner: LifecycleOwner, camera: Camera) {
        camera.cameraInfo.torchState.observe(lifecycleOwner) { state ->
            _isTorchActuallyOn.value = (state == androidx.camera.core.TorchState.ON)
        }
        camera.cameraInfo.zoomState.observe(lifecycleOwner) { zoom ->
            _currentZoomRatio.value = zoom.zoomRatio
        }
    }

    fun setZoomRatio(ratio: Float) {
        val cam = boundCamera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    fun focusAtNormalizedPoint(normX: Float, normY: Float) {
        val cam = boundCamera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB,
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun capturePhoto(
        fixMirrorEnabled: Boolean,
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit,
    ) {
        val imageCapture = imageCaptureUseCase
        if (imageCapture == null) {
            onError("Simultaneous photo capture is not supported during video recording on this camera")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$timestamp")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/LumaCam")
            }
        }

        // FIX MIRROR:
        // When ENABLED: never reverse horizontally (matches actual left-to-right real scene).
        // When DISABLED: mirror front-camera selfie output horizontally; never mirror rear camera.
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = !fixMirrorEnabled && currentLens == LensFacing.FRONT
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            appContext.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues,
        )
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            outputOptions,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    if (savedUri != null) {
                        onSaved(savedUri)
                    } else {
                        onError("Photo saved without URI")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    onError(exception.message ?: "Photo capture failed")
                }
            },
        )
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        selectedAudioDeviceId: Int?,
        hasAudioPermission: Boolean,
        onFinalized: (Uri?) -> Unit,
        onError: (String) -> Unit,
    ) {
        val videoCapture = videoCaptureUseCase
        if (videoCapture == null) {
            onError("Video recorder is not ready")
            return
        }
        if (activeRecording != null) {
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_$timestamp")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LumaCam")
            }
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            appContext.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
            .setContentValues(contentValues)
            .build()

        if (hasAudioPermission) {
            audioInputMonitor.applyAudioRoutingForRecording(selectedAudioDeviceId)
        }

        var pending = videoCapture.output.prepareRecording(appContext, mediaStoreOutput)
        if (hasAudioPermission) {
            // Mandatory reassignment for immutable CameraX PendingRecording API
            pending = pending.withAudioEnabled()
        }

        _recordingState.value = RecordingState.Recording(elapsedMs = 0L, isPaused = false)

        activeRecording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _recordingState.value = RecordingState.Recording(
                        elapsedMs = 0L,
                        isPaused = false,
                    )
                }

                is VideoRecordEvent.Status -> {
                    val stats = event.recordingStats
                    val elapsedMs = stats.recordedDurationNanos / 1_000_000L
                    val currentPaused = (_recordingState.value as? RecordingState.Recording)?.isPaused
                        ?: false
                    _recordingState.value = RecordingState.Recording(
                        elapsedMs = elapsedMs,
                        isPaused = currentPaused,
                        audioAmplitude = stats.audioStats.audioAmplitude,
                    )
                }

                is VideoRecordEvent.Pause -> {
                    val current = _recordingState.value as? RecordingState.Recording
                    _recordingState.value = RecordingState.Recording(
                        elapsedMs = current?.elapsedMs ?: 0L,
                        isPaused = true,
                        audioAmplitude = 0.0,
                    )
                }

                is VideoRecordEvent.Resume -> {
                    val current = _recordingState.value as? RecordingState.Recording
                    _recordingState.value = RecordingState.Recording(
                        elapsedMs = current?.elapsedMs ?: 0L,
                        isPaused = false,
                    )
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    audioInputMonitor.clearAudioRouting()
                    _recordingState.value = RecordingState.Idle
                    if (event.hasError()) {
                        Log.e(TAG, "Video finalize error: ${event.error}", event.cause)
                        onError("Recording failed (code ${event.error})")
                        onFinalized(null)
                    } else {
                        onFinalized(event.outputResults.outputUri)
                    }
                }
            }
        }
    }

    fun togglePauseResumeRecording() {
        val recording = activeRecording ?: return
        val current = _recordingState.value as? RecordingState.Recording ?: return
        if (current.isPaused) {
            recording.resume()
        } else {
            recording.pause()
        }
    }

    fun stopVideoRecording() {
        val recording = activeRecording ?: return
        _recordingState.value = RecordingState.Finalizing
        recording.stop()
    }

    fun release() {
        stopOrientationMonitoring()
        if (lightListenerRegistered) {
            sensorManager?.unregisterListener(lightSensorListener)
            lightListenerRegistered = false
        }
        turnTorchOffImmediately()
        activeRecording?.stop()
        activeRecording = null
        runCatching { cameraProvider?.unbindAll() }
        glProcessor.release()
    }

    private fun resolveSupportedBitrateBps(requestedMbps: Int): Int {
        val maxBps = currentEncoderCaps.maxSupportedBitrateBps
        val requestedBps = requestedMbps.toLong() * 1_000_000L
        return if (maxBps > 0 && requestedBps > maxBps) {
            maxBps
        } else {
            requestedBps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun pickTarget30FpsRange(cameraInfo: CameraInfo): Range<Int>? {
        val ranges = cameraInfo.supportedFrameRateRanges
        if (ranges.isEmpty()) return null
        return ranges.firstOrNull { it.lower == 30 && it.upper == 30 }
            ?: ranges.firstOrNull { 30 in it.lower..it.upper && it.upper <= 30 }
            ?: ranges.firstOrNull { 30 in it.lower..it.upper }
    }

    private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.awaitFuture(): T =
        suspendCancellableCoroutine { cont ->
            addListener(
                {
                    try {
                        cont.resume(get())
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }
                },
                mainExecutor,
            )
            cont.invokeOnCancellation { cancel(false) }
        }

    companion object {
        private const val TAG = "LumaCameraController"
        private const val AUTO_TORCH_DARK_LUX = 12f
        private const val AUTO_TORCH_BRIGHT_LUX = 35f
    }
}
