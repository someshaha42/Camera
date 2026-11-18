package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicExternalOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.camera.model.CameraUiState
import com.example.camera.model.TorchMode

/**
 * Main Camera Settings Sheet.
 * Intentionally excludes HDR settings (HDR remains disabled by default).
 * Includes FIX MIRROR toggle, Bitrate selector, Audio Source selector,
 * Hardware Video Resolution info/selector, Torch Mode, Fixed Pipeline specs,
 * and an independent RESET CAMERA SETTINGS button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
    uiState: CameraUiState,
    onDismiss: () -> Unit,
    onFixMirrorChange: (Boolean) -> Unit,
    onBitrateSelect: (Int) -> Unit,
    onQualitySelect: (String?) -> Unit,
    onAudioDeviceSelect: (Int?) -> Unit,
    onTorchModeSelect: (TorchMode) -> Unit,
    onRequestResetCameraSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val camSettings = uiState.cameraSettings
    val encoderCaps = uiState.encoderCapabilities
    val lensCaps = uiState.lensCapabilities

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            // 1. FIX MIRROR Setting (inside Main Camera Settings, Default ON)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.fix_mirror_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.fix_mirror_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = camSettings.fixMirrorEnabled,
                        onCheckedChange = onFixMirrorChange,
                        modifier = Modifier.testTag("fix_mirror_switch"),
                    )
                }
            }

            // 2. Torch / Flashlight Settings
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Torch / Flashlight",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!lensCaps.hasFlashUnit) {
                    Text(
                        text = "Active camera lens has no hardware flash unit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TorchMode.entries.forEach { mode ->
                        val selected = camSettings.torchMode == mode
                        val label = when (mode) {
                            TorchMode.OFF -> stringResource(R.string.torch_off)
                            TorchMode.AUTO -> stringResource(R.string.torch_auto)
                            TorchMode.ALWAYS_ON -> stringResource(R.string.torch_always_on)
                        }
                        val icon = when (mode) {
                            TorchMode.OFF -> Icons.Rounded.FlashOff
                            TorchMode.AUTO -> Icons.Rounded.FlashAuto
                            TorchMode.ALWAYS_ON -> Icons.Rounded.FlashOn
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onTorchModeSelect(mode) },
                            label = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("torch_${mode.name.lowercase()}_chip"),
                        )
                    }
                }
            }

            // 3. Video Bitrate Settings (100 Mbps default; 150-350 selectable if encoder supports)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.video_bitrate_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Default 100 Mbps • Encoder ceiling: ${encoderCaps.maxSupportedBitrateMbps} Mbps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    encoderCaps.bitrateOptions.forEach { info ->
                        val selected = camSettings.selectedBitrateMbps == info.option.mbps
                        val text = buildString {
                            append(info.option.label)
                            if (info.option.isDefault) append(" (Default)")
                            if (!info.isSupportedByEncoder) append(" • N/A")
                        }
                        FilterChip(
                            selected = selected,
                            enabled = info.isSupportedByEncoder,
                            onClick = { onBitrateSelect(info.option.mbps) },
                            label = { Text(text) },
                            modifier = Modifier.testTag("bitrate_chip_${info.option.mbps}"),
                        )
                    }
                }
            }

            // 4. Audio Source Selection (Internal Mic Default + Live Detected External Mics)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.audio_source_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Records microphone input only (external mics auto-detected when connected).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.audioDevices.forEach { device ->
                        val selected = camSettings.selectedAudioDeviceId == device.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAudioDeviceSelect(device.id) }
                                .minimumInteractiveComponentSize()
                                .testTag("audio_device_${device.id ?: "internal"}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = if (device.isExternal) {
                                            Icons.Outlined.MicExternalOn
                                        } else {
                                            Icons.Outlined.Mic
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        Text(
                                            text = if (device.isExternal) {
                                                "${device.typeLabel} • Connected"
                                            } else {
                                                "${device.typeLabel} • Default"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                RadioButton(
                                    selected = selected,
                                    onClick = { onAudioDeviceSelect(device.id) },
                                )
                            }
                        }
                    }
                }
            }

            // 5. Hardware-Supported Video Resolutions (CameraX Capabilities, zero display dependency)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AspectRatio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.resolution_info_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Auto-detected from active lens hardware (independent of display size)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (lensCaps.supportedResolutions.isEmpty()) {
                    Text(
                        text = "Detecting supported hardware profiles…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lensCaps.supportedResolutions.forEach { res ->
                            val isSelected = camSettings.selectedQualityKey == res.key ||
                                (camSettings.selectedQualityKey == null && res.isMaxSupported)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onQualitySelect(if (res.isMaxSupported) null else res.key)
                                    }
                                    .minimumInteractiveComponentSize()
                                    .testTag("resolution_option_${res.key.lowercase()}"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column {
                                        Text(
                                            text = if (res.isMaxSupported) {
                                                "${res.label} (Max Hardware Auto)"
                                            } else {
                                                res.label
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                        Text(
                                            text = "${res.formattedSize} • Hardware Verified",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Fixed Pipeline Specifications (Non-editable informational readout)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VideoSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.fixed_specs_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    SpecRow("Camera Engine", "AndroidX CameraX Only")
                    SpecRow("Video Codec", encoderCaps.activeCodecLabel)
                    SpecRow("Frame Rate", "${lensCaps.activeFpsLabel} (Fixed)")
                    SpecRow("Color / Dynamic Range", "Standard SDR (HDR Off)")
                    SpecRow(
                        "Simultaneous Video + Photo",
                        if (lensCaps.simultaneousPhotoInVideoSupported) "Supported" else "Hardware Limited",
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Dedicated RESET CAMERA SETTINGS button inside Main Camera Settings
            OutlinedButton(
                onClick = onRequestResetCameraSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("reset_camera_settings_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.reset_camera_settings),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
