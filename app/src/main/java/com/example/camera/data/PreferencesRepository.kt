package com.example.camera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.camera.model.CameraSettings
import com.example.camera.model.ColorFilterPreset
import com.example.camera.model.EffectsSettings
import com.example.camera.model.TorchMode
import com.example.camera.model.VideoBitrateOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cameraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lumacam_preferences",
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        // Effects Settings Keys (independent)
        val PORTRAIT_ENABLED = booleanPreferencesKey("effects_portrait_enabled")
        val BACKGROUND_BLUR = floatPreferencesKey("effects_background_blur")
        val BEAUTY_STRENGTH = floatPreferencesKey("effects_beauty_strength")
        val COLOR_FILTER = stringPreferencesKey("effects_color_filter")

        // Main Camera Settings Keys (independent)
        val VIDEO_BITRATE_MBPS = intPreferencesKey("camera_video_bitrate_mbps")
        val VIDEO_QUALITY_KEY = stringPreferencesKey("camera_video_quality_key")
        val AUDIO_DEVICE_ID = intPreferencesKey("camera_audio_device_id")
        val TORCH_MODE = stringPreferencesKey("camera_torch_mode")
        val FIX_MIRROR_ENABLED = booleanPreferencesKey("camera_fix_mirror_enabled")
    }

    val effectsSettingsFlow: Flow<EffectsSettings> = context.cameraDataStore.data.map { prefs ->
        val filterName = prefs[Keys.COLOR_FILTER]
        val filter = ColorFilterPreset.entries.firstOrNull { it.name == filterName }
            ?: ColorFilterPreset.NONE
        EffectsSettings(
            portraitEnabled = prefs[Keys.PORTRAIT_ENABLED] ?: false,
            backgroundBlurStrength = (prefs[Keys.BACKGROUND_BLUR] ?: 0f).coerceIn(0f, 1f),
            beautyStrength = (prefs[Keys.BEAUTY_STRENGTH] ?: 0f).coerceIn(0f, 1f),
            colorFilter = filter,
        )
    }

    val cameraSettingsFlow: Flow<CameraSettings> = context.cameraDataStore.data.map { prefs ->
        val torchName = prefs[Keys.TORCH_MODE]
        val torchMode = TorchMode.entries.firstOrNull { it.name == torchName } ?: TorchMode.OFF
        val bitrate = prefs[Keys.VIDEO_BITRATE_MBPS] ?: VideoBitrateOption.DEFAULT_MBPS
        val validBitrate = if (VideoBitrateOption.ALL_OPTIONS.any { it.mbps == bitrate }) {
            bitrate
        } else {
            VideoBitrateOption.DEFAULT_MBPS
        }
        CameraSettings(
            selectedBitrateMbps = validBitrate,
            selectedQualityKey = prefs[Keys.VIDEO_QUALITY_KEY],
            selectedAudioDeviceId = prefs[Keys.AUDIO_DEVICE_ID],
            torchMode = torchMode,
            fixMirrorEnabled = prefs[Keys.FIX_MIRROR_ENABLED] ?: true,
        )
    }

    suspend fun setPortraitEnabled(enabled: Boolean) {
        context.cameraDataStore.edit { it[Keys.PORTRAIT_ENABLED] = enabled }
    }

    suspend fun setBackgroundBlur(strength: Float) {
        context.cameraDataStore.edit { it[Keys.BACKGROUND_BLUR] = strength.coerceIn(0f, 1f) }
    }

    suspend fun setBeautyStrength(strength: Float) {
        context.cameraDataStore.edit { it[Keys.BEAUTY_STRENGTH] = strength.coerceIn(0f, 1f) }
    }

    suspend fun setColorFilter(preset: ColorFilterPreset) {
        context.cameraDataStore.edit { it[Keys.COLOR_FILTER] = preset.name }
    }

    suspend fun setVideoBitrateMbps(mbps: Int) {
        context.cameraDataStore.edit { it[Keys.VIDEO_BITRATE_MBPS] = mbps }
    }

    suspend fun setVideoQualityKey(key: String?) {
        context.cameraDataStore.edit { prefs ->
            if (key == null) prefs.remove(Keys.VIDEO_QUALITY_KEY)
            else prefs[Keys.VIDEO_QUALITY_KEY] = key
        }
    }

    suspend fun setAudioDeviceId(deviceId: Int?) {
        context.cameraDataStore.edit { prefs ->
            if (deviceId == null) prefs.remove(Keys.AUDIO_DEVICE_ID)
            else prefs[Keys.AUDIO_DEVICE_ID] = deviceId
        }
    }

    suspend fun setTorchMode(mode: TorchMode) {
        context.cameraDataStore.edit { it[Keys.TORCH_MODE] = mode.name }
    }

    suspend fun setFixMirrorEnabled(enabled: Boolean) {
        context.cameraDataStore.edit { it[Keys.FIX_MIRROR_ENABLED] = enabled }
    }

    /**
     * Resets ONLY Effects settings to defaults. Never touches Main Camera Settings.
     */
    suspend fun resetEffectsSettings() {
        context.cameraDataStore.edit { prefs ->
            prefs.remove(Keys.PORTRAIT_ENABLED)
            prefs.remove(Keys.BACKGROUND_BLUR)
            prefs.remove(Keys.BEAUTY_STRENGTH)
            prefs.remove(Keys.COLOR_FILTER)
        }
    }

    /**
     * Resets ONLY Main Camera Settings to defaults. Never touches Effects Settings.
     */
    suspend fun resetCameraSettings() {
        context.cameraDataStore.edit { prefs ->
            prefs.remove(Keys.VIDEO_BITRATE_MBPS)
            prefs.remove(Keys.VIDEO_QUALITY_KEY)
            prefs.remove(Keys.AUDIO_DEVICE_ID)
            prefs.remove(Keys.TORCH_MODE)
            prefs.remove(Keys.FIX_MIRROR_ENABLED)
        }
    }
}
