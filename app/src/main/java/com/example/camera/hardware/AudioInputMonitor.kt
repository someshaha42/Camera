package com.example.camera.hardware

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.camera.model.AudioInputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors connected microphone hardware inputs in real time.
 * Only microphone input devices are tracked — never internal/system playback audio.
 */
class AudioInputMonitor(context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _availableDevices =
        MutableStateFlow<List<AudioInputDevice>>(listOf(AudioInputDevice.INTERNAL_DEFAULT))
    val availableDevices: StateFlow<List<AudioInputDevice>> = _availableDevices.asStateFlow()

    private val rawExternalDevices = LinkedHashMap<Int, AudioDeviceInfo>()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices()
        }
    }

    init {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
    }

    fun refreshDevices() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        rawExternalDevices.clear()

        val externalList = mutableListOf<AudioInputDevice>()
        for (info in inputs) {
            if (!info.isSource || !isSupportedExternalMicType(info.type)) continue
            rawExternalDevices[info.id] = info
            val product = info.productName?.toString()?.trim().orEmpty()
            val typeLabel = externalTypeLabel(info.type)
            val displayName = if (product.isNotEmpty() && !product.equals(Build.MODEL, ignoreCase = true)) {
                "$product ($typeLabel)"
            } else {
                typeLabel
            }
            externalList.add(
                AudioInputDevice(
                    id = info.id,
                    name = displayName,
                    typeLabel = typeLabel,
                    isExternal = true,
                ),
            )
        }

        _availableDevices.value = listOf(AudioInputDevice.INTERNAL_DEFAULT) +
            externalList.distinctBy { it.id }
    }

    /**
     * Routes microphone input to the selected external microphone when supported on API 31+,
     * or clears routing back to the default internal microphone.
     */
    fun applyAudioRoutingForRecording(selectedDeviceId: Int?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (selectedDeviceId == null) {
            runCatching { audioManager.clearCommunicationDevice() }
            return
        }
        val targetInfo = audioManager.availableCommunicationDevices
            .firstOrNull { it.id == selectedDeviceId }
            ?: rawExternalDevices[selectedDeviceId]

        if (targetInfo != null) {
            runCatching {
                val routed = audioManager.setCommunicationDevice(targetInfo)
                if (!routed) {
                    audioManager.clearCommunicationDevice()
                }
            }
        }
    }

    fun clearAudioRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
    }

    fun release() {
        clearAudioRouting()
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
    }

    private fun isSupportedExternalMicType(type: Int): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE -> true
            else -> false
        }
    }

    private fun externalTypeLabel(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB External Mic"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Mic"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth Mic"
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE -> "Line-In Mic"
            else -> "External Mic"
        }
    }
}
