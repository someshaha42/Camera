package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.FilterVintage
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Portrait
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.camera.model.CameraTab
import com.example.camera.model.CameraUiState
import com.example.camera.model.ColorFilterPreset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsBottomSheet(
    uiState: CameraUiState,
    onDismiss: () -> Unit,
    onPortraitToggle: (Boolean) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onBeautyChange: (Float) -> Unit,
    onColorFilterSelect: (ColorFilterPreset) -> Unit,
    onRequestResetEffects: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val effects = uiState.effectsSettings
    val caps = uiState.lensCapabilities

    val portraitSupportText = when (uiState.activeTab) {
        CameraTab.PHOTO -> when {
            caps.photoBokehExtensionSupported ->
                "CameraX Hardware Bokeh Extension supported on this lens"
            caps.realTimeVideoEffectsSupported ->
                "Hardware Bokeh unavailable; using real-time GPU optical blur"
            else ->
                "Unavailable — neither Bokeh Extension nor real-time effect processing is supported"
        }

        CameraTab.VIDEO -> if (caps.realTimeVideoEffectsSupported) {
            "Real-time GPU video portrait processing active"
        } else {
            "Unavailable — real-time video portrait processing is not supported on this device"
        }
    }

    val glEffectsAvailable = caps.realTimeVideoEffectsSupported

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.effects_panel_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = if (effects.activeEffectCount == 0) {
                                "Natural optical pipeline"
                            } else {
                                "${effects.activeEffectCount} active adjustment(s)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 1. Portrait Mode Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            imageVector = Icons.Rounded.Portrait,
                            contentDescription = null,
                            tint = if (uiState.isPortraitSupportedInCurrentMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.effect_portrait),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = portraitSupportText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.isPortraitSupportedInCurrentMode) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = effects.portraitEnabled,
                        onCheckedChange = onPortraitToggle,
                        enabled = uiState.isPortraitSupportedInCurrentMode,
                        modifier = Modifier.testTag("portrait_mode_switch"),
                    )
                }
            }

            // 2. Background Blur Slider
            EffectSliderRow(
                icon = Icons.Outlined.BlurOn,
                title = stringResource(R.string.effect_background_blur),
                value = effects.backgroundBlurStrength,
                enabled = glEffectsAvailable,
                unavailableReason = if (!glEffectsAvailable) {
                    "Real-time shader processing unavailable"
                } else {
                    null
                },
                sliderTestTag = "background_blur_slider",
                onValueChange = onBackgroundBlurChange,
            )

            // 3. Beauty Effects Slider
            EffectSliderRow(
                icon = Icons.Outlined.FaceRetouchingNatural,
                title = stringResource(R.string.effect_beauty),
                value = effects.beautyStrength,
                enabled = glEffectsAvailable,
                unavailableReason = if (!glEffectsAvailable) {
                    "Real-time shader processing unavailable"
                } else {
                    null
                },
                sliderTestTag = "beauty_effect_slider",
                onValueChange = onBeautyChange,
            )

            // 4. Color Filters
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterVintage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.effect_color_filters),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (!glEffectsAvailable) {
                        Text(
                            text = "(Unavailable)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ColorFilterPreset.entries) { preset ->
                        val selected = effects.colorFilter == preset
                        ColorFilterChip(
                            preset = preset,
                            selected = selected,
                            enabled = glEffectsAvailable || preset == ColorFilterPreset.NONE,
                            onClick = { onColorFilterSelect(preset) },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Dedicated RESET EFFECTS button inside Effects section
            OutlinedButton(
                onClick = onRequestResetEffects,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("reset_effects_button"),
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
                    text = stringResource(R.string.reset_effects),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EffectSliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: Float,
    enabled: Boolean,
    unavailableReason: String?,
    sliderTestTag: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                text = unavailableReason ?: "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = if (unavailableReason != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(sliderTestTag),
        )
    }
}

@Composable
private fun ColorFilterChip(
    preset: ColorFilterPreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val previewColors = when (preset) {
        ColorFilterPreset.NONE -> listOf(Color(0xFF606875), Color(0xFF9EA6B5))
        ColorFilterPreset.VIVID -> listOf(Color(0xFFFF5E57), Color(0xFF00D2D3))
        ColorFilterPreset.CINEMA -> listOf(Color(0xFF0A4F6E), Color(0xFFE69D45))
        ColorFilterPreset.NOIR -> listOf(Color(0xFF141414), Color(0xFFD8D8D8))
        ColorFilterPreset.WARM_FILM -> listOf(Color(0xFFD9822B), Color(0xFFF6C88F))
        ColorFilterPreset.COOL_MIST -> listOf(Color(0xFF4B7BEC), Color(0xFFB8D8FA))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("filter_chip_${preset.name.lowercase()}"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(previewColors)),
                contentAlignment = Alignment.Center,
            ) {
                if (preset == ColorFilterPreset.NONE) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
