package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.camera.data.PreferencesRepository
import com.example.camera.model.ColorFilterPreset
import com.example.camera.model.TorchMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches LumaCam`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LumaCam", appName)
    }

    @Test
    fun `resetEffectsSettings resets only effects without modifying camera settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)

        // Customize both camera settings and effects
        repo.setVideoBitrateMbps(250)
        repo.setTorchMode(TorchMode.ALWAYS_ON)
        repo.setFixMirrorEnabled(false)
        repo.setAudioDeviceId(42)
        repo.setPortraitEnabled(true)
        repo.setBackgroundBlur(0.8f)
        repo.setBeautyStrength(0.6f)
        repo.setColorFilter(ColorFilterPreset.CINEMA)

        // Reset ONLY Effects
        repo.resetEffectsSettings()

        val fx = repo.effectsSettingsFlow.first()
        assertFalse(fx.portraitEnabled)
        assertEquals(0f, fx.backgroundBlurStrength, 0.001f)
        assertEquals(0f, fx.beautyStrength, 0.001f)
        assertEquals(ColorFilterPreset.NONE, fx.colorFilter)

        // Verify Main Camera Settings stayed untouched
        val cam = repo.cameraSettingsFlow.first()
        assertEquals(250, cam.selectedBitrateMbps)
        assertEquals(TorchMode.ALWAYS_ON, cam.torchMode)
        assertFalse(cam.fixMirrorEnabled)
        assertEquals(42, cam.selectedAudioDeviceId)
    }

    @Test
    fun `resetCameraSettings resets only camera settings without modifying effects`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)

        // Customize both camera settings and effects
        repo.setVideoBitrateMbps(300)
        repo.setTorchMode(TorchMode.AUTO)
        repo.setFixMirrorEnabled(false)
        repo.setAudioDeviceId(99)
        repo.setVideoQualityKey("FHD")
        repo.setPortraitEnabled(true)
        repo.setBackgroundBlur(0.5f)
        repo.setColorFilter(ColorFilterPreset.NOIR)

        // Reset ONLY Camera Settings
        repo.resetCameraSettings()

        val cam = repo.cameraSettingsFlow.first()
        assertEquals(100, cam.selectedBitrateMbps)
        assertEquals(TorchMode.OFF, cam.torchMode)
        assertTrue(cam.fixMirrorEnabled)
        assertNull(cam.selectedAudioDeviceId)
        assertNull(cam.selectedQualityKey)

        // Verify Effects stayed untouched
        val fx = repo.effectsSettingsFlow.first()
        assertTrue(fx.portraitEnabled)
        assertEquals(0.5f, fx.backgroundBlurStrength, 0.001f)
        assertEquals(ColorFilterPreset.NOIR, fx.colorFilter)
    }
}
