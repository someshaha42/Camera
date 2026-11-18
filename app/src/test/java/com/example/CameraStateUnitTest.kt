package com.example

import com.example.camera.model.AudioInputDevice
import com.example.camera.model.BitrateSupportInfo
import com.example.camera.model.CameraSettings
import com.example.camera.model.CameraTab
import com.example.camera.model.CameraUiState
import com.example.camera.model.EncoderCodecCapabilities
import com.example.camera.model.TorchMode
import com.example.camera.model.VideoBitrateOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraStateUnitTest {

    @Test
    fun `default ui state opens in VIDEO mode with 100 Mbps, OFF torch, internal mic, and FIX MIRROR ON`() {
        val state = CameraUiState()
        assertEquals(CameraTab.VIDEO, state.activeTab)
        assertEquals(100, state.cameraSettings.selectedBitrateMbps)
        assertEquals(TorchMode.OFF, state.cameraSettings.torchMode)
        assertTrue(state.cameraSettings.fixMirrorEnabled)
        assertEquals(AudioInputDevice.INTERNAL_DEFAULT, state.activeAudioDevice)
        assertFalse(state.effectsSettings.hasActiveGlEffects)
    }

    @Test
    fun `bitrate options cover 100 to 350 Mbps with 100 as default`() {
        val values = VideoBitrateOption.ALL_OPTIONS.map { it.mbps }
        assertEquals(listOf(100, 150, 200, 250, 300, 350), values)
        assertTrue(VideoBitrateOption.ALL_OPTIONS.first().isDefault)
        assertFalse(VideoBitrateOption.ALL_OPTIONS.drop(1).any { it.isDefault })
    }

    @Test
    fun `effective bitrate clamps unsupported high bitrate back to supported range`() {
        val limitedEncoder = EncoderCodecCapabilities(
            hevcSupported = true,
            maxSupportedBitrateBps = 200_000_000,
            maxSupportedBitrateMbps = 200,
            bitrateOptions = VideoBitrateOption.ALL_OPTIONS.map {
                BitrateSupportInfo(it, isSupportedByEncoder = it.mbps <= 200)
            },
        )
        val state = CameraUiState(
            cameraSettings = CameraSettings(selectedBitrateMbps = 350),
            encoderCapabilities = limitedEncoder,
        )
        assertEquals(100, state.effectiveBitrateMbps)
    }
}
