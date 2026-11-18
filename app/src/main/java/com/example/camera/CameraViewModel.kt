package com.example.camera

import android.app.Application
import android.net.Uri
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.data.PreferencesRepository
import com.example.camera.hardware.AudioInputMonitor
import com.example.camera.model.CameraSettings
import com.example.camera.model.CameraTab
import com.example.camera.model.CameraUiState
import com.example.camera.model.ColorFilterPreset
import com.example.camera.model.EffectsSettings
import com.example.camera.model.LensFacing
import com.example.camera.model.RecordingState
import com.example.camera.model.TorchMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)
    private val audioInputMonitor = AudioInputMonitor(application)
    private val cameraController = CameraController(application, audioInputMonitor)

    private val _uiState = MutableStateFlow(
        CameraUiState(
            encoderCapabilities = cameraController.getEncoderCapabilities(),
        ),
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    val surfaceRequest: StateFlow<SurfaceRequest?> = cameraController.surfaceRequest

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var rebindJob: Job? = null
    private var focusClearJob: Job? = null
    private var lastRebindSignature: String? = null

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.cameraSettingsFlow,
                preferencesRepository.effectsSettingsFlow,
                audioInputMonitor.availableDevices,
                cameraController.recordingState,
                cameraController.currentZoomRatio,
            ) { camSettings, fxSettings, audioDevices, recState, zoom ->
                CombinedPreferencesAndHardware(
                    cameraSettings = camSettings,
                    effectsSettings = fxSettings,
                    audioDevices = audioDevices,
                    recordingState = recState,
                    zoomRatio = zoom,
                )
            }.collect { combined ->
                val previousCam = _uiState.value.cameraSettings
                val previousFx = _uiState.value.effectsSettings
                val previousAudio = _uiState.value.audioDevices

                // Auto-notify when a new external mic appears
                if (combined.audioDevices.size > previousAudio.size && previousAudio.isNotEmpty()) {
                    val newlyAdded = combined.audioDevices.firstOrNull { newDev ->
                        newDev.isExternal && previousAudio.none { it.id == newDev.id }
                    }
                    if (newlyAdded != null) {
                        _messages.tryEmit("External mic detected: ${newlyAdded.name}")
                    }
                }

                // If currently selected external mic disconnected, revert gracefully to Internal Mic
                val selectedId = combined.cameraSettings.selectedAudioDeviceId
                if (selectedId != null && combined.audioDevices.none { it.id == selectedId }) {
                    preferencesRepository.setAudioDeviceId(null)
                    _messages.tryEmit("External mic disconnected — switched to Internal Microphone")
                }

                _uiState.update {
                    it.copy(
                        cameraSettings = combined.cameraSettings,
                        effectsSettings = combined.effectsSettings,
                        audioDevices = combined.audioDevices,
                        recordingState = combined.recordingState,
                        currentZoomRatio = combined.zoomRatio,
                    )
                }

                if (previousFx != combined.effectsSettings) {
                    cameraController.updateLiveEffects(combined.effectsSettings)
                    // Only rebind if Portrait Extension changed while in PHOTO mode
                    if (_uiState.value.activeTab == CameraTab.PHOTO &&
                        previousFx.portraitEnabled != combined.effectsSettings.portraitEnabled
                    ) {
                        triggerBind()
                    }
                }

                if (previousCam.torchMode != combined.cameraSettings.torchMode) {
                    cameraController.updateTorchMode(combined.cameraSettings.torchMode)
                }

                // Rebind CameraX if structural camera configuration changed (and not mid-recording)
                if (previousCam.selectedBitrateMbps != combined.cameraSettings.selectedBitrateMbps ||
                    previousCam.selectedQualityKey != combined.cameraSettings.selectedQualityKey ||
                    previousCam.fixMirrorEnabled != combined.cameraSettings.fixMirrorEnabled
                ) {
                    if (combined.recordingState is RecordingState.Idle) {
                        triggerBind()
                    }
                }
            }
        }

        viewModelScope.launch {
            cameraController.isTorchActuallyOn.collect { isOn ->
                _uiState.update { it.copy(isTorchActuallyOn = isOn) }
            }
        }

        viewModelScope.launch {
            cameraController.isAutoTorchDarkScene.collect { isDark ->
                _uiState.update { it.copy(isAutoTorchDarkScene = isDark) }
            }
        }
    }

    fun attachLifecycleAndBind(lifecycleOwner: LifecycleOwner) {
        currentLifecycleOwner = lifecycleOwner
        cameraController.startOrientationMonitoring()
        triggerBind(force = true)
    }

    fun onAppBackgrounded() {
        cameraController.turnTorchOffImmediately()
        cameraController.stopOrientationMonitoring()
    }

    fun onAppForegrounded() {
        cameraController.startOrientationMonitoring()
        cameraController.applyTorchState()
    }

    fun selectTab(tab: CameraTab) {
        if (_uiState.value.activeTab == tab) return
        if (_uiState.value.recordingState !is RecordingState.Idle) {
            _messages.tryEmit("Stop video recording before switching camera modes")
            return
        }
        _uiState.update { it.copy(activeTab = tab) }
        triggerBind(force = true)
    }

    fun switchCameraLens() {
        if (_uiState.value.recordingState !is RecordingState.Idle) {
            _messages.tryEmit("Stop video recording before switching cameras")
            return
        }
        val caps = _uiState.value.lensCapabilities
        val nextLens = if (_uiState.value.lensFacing == LensFacing.BACK) {
            if (caps.hasFrontCamera) LensFacing.FRONT else {
                _messages.tryEmit("Front camera is not available on this device")
                return
            }
        } else {
            if (caps.hasBackCamera) LensFacing.BACK else {
                _messages.tryEmit("Back camera is not available on this device")
                return
            }
        }
        _uiState.update { it.copy(lensFacing = nextLens) }
        triggerBind(force = true)
    }

    fun cycleTorchMode() {
        val next = when (_uiState.value.cameraSettings.torchMode) {
            TorchMode.OFF -> TorchMode.AUTO
            TorchMode.AUTO -> TorchMode.ALWAYS_ON
            TorchMode.ALWAYS_ON -> TorchMode.OFF
        }
        setTorchMode(next)
    }

    fun setTorchMode(mode: TorchMode) {
        if (mode != TorchMode.OFF && !_uiState.value.lensCapabilities.hasFlashUnit) {
            _messages.tryEmit("Torch is not available on this camera lens")
        }
        viewModelScope.launch {
            preferencesRepository.setTorchMode(mode)
        }
    }

    fun setVideoBitrateMbps(mbps: Int) {
        val option = _uiState.value.encoderCapabilities.bitrateOptions
            .firstOrNull { it.option.mbps == mbps }
        if (option != null && !option.isSupportedByEncoder) {
            _messages.tryEmit("$mbps Mbps exceeds this device encoder limit (${_uiState.value.encoderCapabilities.maxSupportedBitrateMbps} Mbps)")
            return
        }
        viewModelScope.launch {
            preferencesRepository.setVideoBitrateMbps(mbps)
        }
    }

    fun setVideoQualityKey(key: String?) {
        viewModelScope.launch {
            preferencesRepository.setVideoQualityKey(key)
        }
    }

    fun setAudioDeviceId(deviceId: Int?) {
        viewModelScope.launch {
            preferencesRepository.setAudioDeviceId(deviceId)
        }
    }

    fun setFixMirrorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFixMirrorEnabled(enabled)
        }
    }

    fun setPortraitEnabled(enabled: Boolean) {
        if (enabled && !_uiState.value.isPortraitSupportedInCurrentMode) {
            _messages.tryEmit("Portrait effect is unavailable on this hardware configuration")
            return
        }
        viewModelScope.launch {
            preferencesRepository.setPortraitEnabled(enabled)
        }
    }

    fun setBackgroundBlur(strength: Float) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundBlur(strength)
        }
    }

    fun setBeautyStrength(strength: Float) {
        viewModelScope.launch {
            preferencesRepository.setBeautyStrength(strength)
        }
    }

    fun setColorFilter(preset: ColorFilterPreset) {
        viewModelScope.launch {
            preferencesRepository.setColorFilter(preset)
        }
    }

    /**
     * Resets ONLY Effects settings to defaults; preserves all Main Camera Settings.
     */
    fun resetEffectsSettings() {
        viewModelScope.launch {
            preferencesRepository.resetEffectsSettings()
            _messages.tryEmit("Effects settings reset to defaults")
        }
    }

    /**
     * Resets ONLY Main Camera Settings to defaults; preserves all Effects Settings.
     */
    fun resetCameraSettings() {
        viewModelScope.launch {
            preferencesRepository.resetCameraSettings()
            _messages.tryEmit("Camera settings reset to defaults")
        }
    }

    fun setZoomRatio(ratio: Float) {
        cameraController.setZoomRatio(ratio)
    }

    fun tapToFocus(normX: Float, normY: Float, previewNormX: Float, previewNormY: Float) {
        cameraController.focusAtNormalizedPoint(normX, normY)
        _uiState.update { it.copy(focusPointNormalized = previewNormX to previewNormY) }
        focusClearJob?.cancel()
        focusClearJob = viewModelScope.launch {
            delay(1600)
            _uiState.update { it.copy(focusPointNormalized = null) }
        }
    }

    fun capturePhoto() {
        if (_uiState.value.isPhotoCapturing) return
        _uiState.update { it.copy(isPhotoCapturing = true) }
        cameraController.capturePhoto(
            fixMirrorEnabled = _uiState.value.cameraSettings.fixMirrorEnabled,
            onSaved = { uri ->
                _uiState.update {
                    it.copy(
                        isPhotoCapturing = false,
                        lastCapturedUri = uri.toString(),
                        lastCaptureIsVideo = false,
                    )
                }
                _messages.tryEmit("Photo saved")
            },
            onError = { err ->
                _uiState.update { it.copy(isPhotoCapturing = false) }
                _messages.tryEmit(err)
            },
        )
    }

    fun toggleVideoRecording(hasAudioPermission: Boolean) {
        when (_uiState.value.recordingState) {
            is RecordingState.Idle -> {
                if (!hasAudioPermission) {
                    _messages.tryEmit("Recording video without audio (microphone permission denied)")
                }
                cameraController.startVideoRecording(
                    selectedAudioDeviceId = _uiState.value.cameraSettings.selectedAudioDeviceId,
                    hasAudioPermission = hasAudioPermission,
                    onFinalized = { uri: Uri? ->
                        if (uri != null) {
                            _uiState.update {
                                it.copy(
                                    lastCapturedUri = uri.toString(),
                                    lastCaptureIsVideo = true,
                                )
                            }
                            _messages.tryEmit("H.265 Video saved")
                        }
                    },
                    onError = { err ->
                        _messages.tryEmit(err)
                    },
                )
            }

            is RecordingState.Recording -> {
                cameraController.stopVideoRecording()
            }

            is RecordingState.Finalizing -> Unit
        }
    }

    fun togglePauseResumeRecording() {
        cameraController.togglePauseResumeRecording()
    }

    private fun triggerBind(force: Boolean = false) {
        val owner = currentLifecycleOwner ?: return
        val state = _uiState.value
        val signature = listOf(
            state.activeTab,
            state.lensFacing,
            state.cameraSettings.selectedBitrateMbps,
            state.cameraSettings.selectedQualityKey,
            state.cameraSettings.fixMirrorEnabled,
            if (state.activeTab == CameraTab.PHOTO) state.effectsSettings.portraitEnabled else false,
        ).joinToString("|")

        if (!force && signature == lastRebindSignature) return
        lastRebindSignature = signature

        rebindJob?.cancel()
        rebindJob = viewModelScope.launch {
            val result = cameraController.bindCamera(
                lifecycleOwner = owner,
                tab = state.activeTab,
                lensFacing = state.lensFacing,
                cameraSettings = state.cameraSettings,
                effectsSettings = state.effectsSettings,
            )
            result.onSuccess { caps ->
                _uiState.update {
                    it.copy(
                        lensFacing = caps.lensFacing,
                        lensCapabilities = caps,
                        cameraError = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(cameraError = throwable.message ?: "Unable to initialize CameraX")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraController.release()
        audioInputMonitor.release()
    }

    private data class CombinedPreferencesAndHardware(
        val cameraSettings: CameraSettings,
        val effectsSettings: EffectsSettings,
        val audioDevices: List<com.example.camera.model.AudioInputDevice>,
        val recordingState: RecordingState,
        val zoomRatio: Float,
    )
}
