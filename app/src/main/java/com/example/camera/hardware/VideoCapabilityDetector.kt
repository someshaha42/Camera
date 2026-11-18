package com.example.camera.hardware

import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import com.example.camera.model.BitrateSupportInfo
import com.example.camera.model.EncoderCodecCapabilities
import com.example.camera.model.HardwareResolution
import com.example.camera.model.VideoBitrateOption

/**
 * Queries real camera hardware video resolutions (via CameraX VideoCapabilities & QualitySelector)
 * and real H.265/HEVC encoder bitrate ranges (via MediaCodecList).
 * Never hardcodes resolutions or derives resolution from display metrics.
 */
object VideoCapabilityDetector {

    fun queryHardwareResolutions(cameraInfo: CameraInfo): List<HardwareResolution> {
        val videoCaps = Recorder.getVideoCapabilities(cameraInfo)
        val supportedQualities = videoCaps.getSupportedQualities(DynamicRange.SDR)
        if (supportedQualities.isEmpty()) return emptyList()

        val rawList = supportedQualities.mapNotNull { quality ->
            val size = QualitySelector.getResolution(cameraInfo, quality) ?: return@mapNotNull null
            HardwareResolution(
                quality = quality,
                key = qualityKey(quality),
                label = qualityDisplayLabel(quality, size.width, size.height),
                width = size.width,
                height = size.height,
                isMaxSupported = false,
            )
        }.sortedByDescending { it.pixelCount }

        return rawList.mapIndexed { index, res ->
            res.copy(isMaxSupported = index == 0)
        }
    }

    fun queryEncoderCapabilities(): EncoderCodecCapabilities {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var hevcSupported = false
        var maxHevcBitrateBps = 0

        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) continue
            val supportsHevc = codecInfo.supportedTypes.any {
                it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)
            }
            if (!supportsHevc) continue

            hevcSupported = true
            val caps = runCatching {
                codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            }.getOrNull() ?: continue

            val upper = caps.videoCapabilities?.bitrateRange?.upper ?: 0
            if (upper > maxHevcBitrateBps) {
                maxHevcBitrateBps = upper
            }
        }

        // If HEVC is absent on an emulator/older device, inspect AVC fallback bitrate honestly
        var maxFallbackBitrateBps = maxHevcBitrateBps
        if (!hevcSupported) {
            for (codecInfo in codecList.codecInfos) {
                if (!codecInfo.isEncoder) continue
                val supportsAvc = codecInfo.supportedTypes.any {
                    it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
                }
                if (!supportsAvc) continue
                val caps = runCatching {
                    codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                }.getOrNull() ?: continue
                val upper = caps.videoCapabilities?.bitrateRange?.upper ?: 0
                if (upper > maxFallbackBitrateBps) {
                    maxFallbackBitrateBps = upper
                }
            }
        }

        val effectiveUpperBps = maxFallbackBitrateBps.coerceAtLeast(0)
        val effectiveUpperMbps = effectiveUpperBps / 1_000_000

        val bitrateOptions = VideoBitrateOption.ALL_OPTIONS.map { option ->
            val supported = effectiveUpperBps >= option.bps
            BitrateSupportInfo(
                option = option,
                isSupportedByEncoder = supported,
            )
        }

        return EncoderCodecCapabilities(
            hevcSupported = hevcSupported,
            activeCodecLabel = if (hevcSupported) {
                "H.265 (HEVC)"
            } else {
                "H.265 (HEVC) — H.264 Fallback"
            },
            maxSupportedBitrateBps = effectiveUpperBps,
            maxSupportedBitrateMbps = effectiveUpperMbps,
            bitrateOptions = bitrateOptions,
        )
    }

    private fun qualityKey(quality: Quality): String = when (quality) {
        Quality.UHD -> "UHD"
        Quality.FHD -> "FHD"
        Quality.HD -> "HD"
        Quality.SD -> "SD"
        else -> quality.toString()
    }

    private fun qualityDisplayLabel(quality: Quality, width: Int, height: Int): String =
        when (quality) {
            Quality.UHD -> "4K UHD"
            Quality.FHD -> "1080p FHD"
            Quality.HD -> "720p HD"
            Quality.SD -> "480p SD"
            else -> "${minOf(width, height)}p"
        }
}
