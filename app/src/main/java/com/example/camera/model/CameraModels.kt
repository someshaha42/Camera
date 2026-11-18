package com.example.camera.model

import androidx.camera.video.Quality

enum class CameraTab {
    VIDEO,
    PHOTO,
}

enum class LensFacing {
    BACK,
    FRONT,
}

enum class TorchMode {
    OFF,
    AUTO,
    ALWAYS_ON,
}

enum class ColorFilterPreset(val displayName: String) {
    NONE("Original"),
    VIVID("Vivid"),
    CINEMA("Cinema"),
    NOIR("Noir"),
    WARM_FILM("Warm Film"),
    COOL_MIST("Cool Mist"),
}

data class VideoBitrateOption(
    val mbps: Int,
    val isDefault: Boolean = false,
) {
    val bps: Long get() = mbps.toLong() * 1_000_000L
    val label: String get() = "$mbps Mbps"

    companion object {
        const val DEFAULT_MBPS = 100
        val ALL_OPTIONS = listOf(
            VideoBitrateOption(100, isDefault = true),
            VideoBitrateOption(150),
            VideoBitrateOption(200),
            VideoBitrateOption(250),
            VideoBitrateOption(300),
            VideoBitrateOption(350),
        )
    }
}

data class BitrateSupportInfo(
    val option: VideoBitrateOption,
    val isSupportedByEncoder: Boolean,
)

data class HardwareResolution(
    val quality: Quality,
    val key: String,
    val label: String,
    val width: Int,
    val height: Int,
    val isMaxSupported: Boolean,
) {
    val pixelCount: Long get() = width.toLong() * height.toLong()
    val formattedSize: String get() = "${width}×${height}"
}

data class AudioInputDevice(
    val id: Int?,
    val name: String,
    val typeLabel: String,
    val isExternal: Boolean,
) {
    companion object {
        val INTERNAL_DEFAULT = AudioInputDevice(
            id = null,
            name = "Internal Microphone",
            typeLabel = "Built-in Mic",
            isExternal = false,
        )
    }
}

/**
 * Effects settings — completely independent from Main Camera Settings.
 */
data class EffectsSettings(
    val portraitEnabled: Boolean = false,
    val backgroundBlurStrength: Float = 0f,
    val beautyStrength: Float = 0f,
    val colorFilter: ColorFilterPreset = ColorFilterPreset.NONE,
) {
    val hasActiveGlEffects: Boolean
        get() = backgroundBlurStrength > 0.01f ||
            beautyStrength > 0.01f ||
            colorFilter != ColorFilterPreset.NONE ||
            portraitEnabled

    val activeEffectCount: Int
        get() = listOf(
            portraitEnabled,
            backgroundBlurStrength > 0.01f,
            beautyStrength > 0.01f,
            colorFilter != ColorFilterPreset.NONE,
        ).count { it }
}

/**
 * Main Camera Settings — completely independent from Effects Settings.
 * Note: HDR is intentionally absent (disabled by default per fixed camera specification).
 * Video Codec (H.265 HEVC) and Frame Rate (30 FPS) are fixed pipeline constants.
 */
data class CameraSettings(
    val selectedBitrateMbps: Int = VideoBitrateOption.DEFAULT_MBPS,
    val selectedQualityKey: String? = null, // null = Auto Maximum Supported Hardware Resolution
    val selectedAudioDeviceId: Int? = null, // null = Internal Microphone (default)
    val torchMode: TorchMode = TorchMode.OFF,
    val fixMirrorEnabled: Boolean = true, // Default ON
)

data class EncoderCodecCapabilities(
    val hevcSupported: Boolean = false,
    val activeCodecLabel: String = "H.265 (HEVC)",
    val maxSupportedBitrateBps: Int = 100_000_000,
    val maxSupportedBitrateMbps: Int = 100,
    val bitrateOptions: List<BitrateSupportInfo> = VideoBitrateOption.ALL_OPTIONS.map {
        BitrateSupportInfo(it, it.mbps == 100)
    },
)

data class LensHardwareCapabilities(
    val lensFacing: LensFacing = LensFacing.BACK,
    val hasBackCamera: Boolean = true,
    val hasFrontCamera: Boolean = true,
    val hasFlashUnit: Boolean = false,
    val supportedResolutions: List<HardwareResolution> = emptyList(),
    val maxResolution: HardwareResolution? = null,
    val photoBokehExtensionSupported: Boolean = false,
    val simultaneousPhotoInVideoSupported: Boolean = true,
    val realTimeVideoEffectsSupported: Boolean = true,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val zoomPresets: List<Float> = listOf(1f),
    val activeFpsLabel: String = "30 FPS",
)

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(
        val elapsedMs: Long,
        val isPaused: Boolean = false,
        val audioAmplitude: Double = 0.0,
    ) : RecordingState
    data object Finalizing : RecordingState
}

data class CameraUiState(
    val activeTab: CameraTab = CameraTab.VIDEO,
    val lensFacing: LensFacing = LensFacing.BACK,
    val effectsSettings: EffectsSettings = EffectsSettings(),
    val cameraSettings: CameraSettings = CameraSettings(),
    val encoderCapabilities: EncoderCodecCapabilities = EncoderCodecCapabilities(),
    val lensCapabilities: LensHardwareCapabilities = LensHardwareCapabilities(),
    val audioDevices: List<AudioInputDevice> = listOf(AudioInputDevice.INTERNAL_DEFAULT),
    val recordingState: RecordingState = RecordingState.Idle,
    val currentZoomRatio: Float = 1f,
    val isTorchActuallyOn: Boolean = false,
    val isAutoTorchDarkScene: Boolean = false,
    val isPhotoCapturing: Boolean = false,
    val lastCapturedUri: String? = null,
    val lastCaptureIsVideo: Boolean = false,
    val focusPointNormalized: Pair<Float, Float>? = null,
    val cameraError: String? = null,
) {
    val activeResolution: HardwareResolution?
        get() = lensCapabilities.supportedResolutions.firstOrNull {
            it.key == cameraSettings.selectedQualityKey
        } ?: lensCapabilities.maxResolution

    val effectiveBitrateMbps: Int
        get() {
            val requested = cameraSettings.selectedBitrateMbps
            val supported = encoderCapabilities.bitrateOptions
                .filter { it.isSupportedByEncoder }
                .map { it.option.mbps }
            return when {
                requested in supported -> requested
                VideoBitrateOption.DEFAULT_MBPS in supported -> VideoBitrateOption.DEFAULT_MBPS
                supported.isNotEmpty() -> supported.max()
                else -> VideoBitrateOption.DEFAULT_MBPS
            }
        }

    val activeAudioDevice: AudioInputDevice
        get() = audioDevices.firstOrNull { it.id == cameraSettings.selectedAudioDeviceId }
            ?: AudioInputDevice.INTERNAL_DEFAULT

    val isPortraitSupportedInCurrentMode: Boolean
        get() = when (activeTab) {
            CameraTab.PHOTO -> lensCapabilities.photoBokehExtensionSupported ||
                lensCapabilities.realTimeVideoEffectsSupported
            CameraTab.VIDEO -> lensCapabilities.realTimeVideoEffectsSupported
        }
}
