import androidx.compose.ui.draw.clip
package com.ascify.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ascify.app.settings.*
import com.ascify.app.ui.theme.ASCIIColors

@Composable
fun SettingsPanel(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onCharacterSetChange: (CharacterSet) -> Unit,
    onColorPaletteChange: (ColorPalette) -> Unit,
    onDensityChange: (AsciiDensity) -> Unit,
    onExportFormatChange: (ExportFormat) -> Unit,
    onEdgeEnhancementChange: (Boolean) -> Unit,
    onNightModeChange: (Boolean) -> Unit,
    onAdaptiveRenderingChange: (Boolean) -> Unit,
    onSaveOriginalChange: (Boolean) -> Unit,
    onShowFpsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(onClick = onDismiss)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .background(
                    color = ASCIIColors.SurfaceBlack,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .border(
                    width = 0.5.dp,
                    color = ASCIIColors.NeonGreen.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .clickable(enabled = false) {} // Absorb clicks
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "[ SETTINGS ]",
                            color = ASCIIColors.NeonGreen,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "ASCII LENS v1.0",
                            color = ASCIIColors.TextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = ASCIIColors.TextSecondary)
                    }
                }

                Spacer(Modifier.height(24.dp))
                NeonDivider()
                Spacer(Modifier.height(20.dp))

                // ── Rendering ───────────────────────────────────────────────
                SectionHeader("RENDERING")

                OptionRow(label = "ASCII Density") {
                    ChipGroup(
                        options = AsciiDensity.values().map { it.displayName },
                        selected = settings.asciiDensity.ordinal,
                        onSelect = { AsciiDensity.values().getOrNull(it)?.let(onDensityChange) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                OptionRow(label = "Character Set") {
                    ChipGroup(
                        options = CharacterSet.values().map { it.displayName },
                        selected = settings.characterSet.ordinal,
                        onSelect = { CharacterSet.values().getOrNull(it)?.let(onCharacterSetChange) }
                    )
                }

                Spacer(Modifier.height(20.dp))
                NeonDivider()
                Spacer(Modifier.height(20.dp))

                // ── Color ────────────────────────────────────────────────────
                SectionHeader("COLOR PALETTE")

                ColorPaletteGrid(
                    selected = settings.colorPalette,
                    onSelect = onColorPaletteChange
                )

                Spacer(Modifier.height(20.dp))
                NeonDivider()
                Spacer(Modifier.height(20.dp))

                // ── Export ───────────────────────────────────────────────────
                SectionHeader("EXPORT")

                OptionRow(label = "Format") {
                    ChipGroup(
                        options = ExportFormat.values().map { it.name },
                        selected = settings.exportFormat.ordinal,
                        onSelect = { ExportFormat.values().getOrNull(it)?.let(onExportFormatChange) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                ToggleRow(
                    label = "Save Original Frame",
                    subtitle = "Also save the unprocessed camera frame",
                    checked = settings.saveOriginalFrame,
                    onCheckedChange = onSaveOriginalChange
                )

                Spacer(Modifier.height(20.dp))
                NeonDivider()
                Spacer(Modifier.height(20.dp))

                // ── Advanced ─────────────────────────────────────────────────
                SectionHeader("ADVANCED")

                ToggleRow(
                    label = "Edge Enhancement",
                    subtitle = "Sobel filter sharpens ASCII boundaries",
                    checked = settings.edgeEnhancement,
                    onCheckedChange = onEdgeEnhancementChange
                )

                Spacer(Modifier.height(12.dp))

                ToggleRow(
                    label = "Night Mode Tuning",
                    subtitle = "Boosts shadows for low light scenes",
                    checked = settings.nightModeEnabled,
                    onCheckedChange = onNightModeChange
                )

                Spacer(Modifier.height(12.dp))

                ToggleRow(
                    label = "Adaptive Rendering",
                    subtitle = "Auto-reduce density on slower devices",
                    checked = settings.adaptiveRendering,
                    onCheckedChange = onAdaptiveRenderingChange
                )

                Spacer(Modifier.height(12.dp))

                ToggleRow(
                    label = "Show FPS Counter",
                    subtitle = "Display render FPS in the viewfinder",
                    checked = settings.showFpsCounter,
                    onCheckedChange = onShowFpsChange
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─── Subcomponents ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        color = ASCIIColors.NeonGreen.copy(alpha = 0.6f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(bottom = 14.dp)
    )
}

@Composable
fun NeonDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(ASCIIColors.NeonGreen.copy(alpha = 0.2f))
    )
}

@Composable
fun OptionRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            color = ASCIIColors.TextSecondary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        content()
    }
}

@Composable
fun ChipGroup(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) ASCIIColors.NeonGreenFaint else ASCIIColors.SurfaceElevated
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSelected) ASCIIColors.NeonGreen else ASCIIColors.TextMuted,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) ASCIIColors.NeonGreen else ASCIIColors.TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun ColorPaletteGrid(
    selected: ColorPalette,
    onSelect: (ColorPalette) -> Unit
) {
    val palettes = ColorPalette.values()
    val paletteColors = mapOf(
        ColorPalette.FULL_RGB to listOf(Color.Red, Color.Green, Color.Blue),
        ColorPalette.GREEN_TERMINAL to listOf(Color(0xFF00FF41), Color(0xFF003B00)),
        ColorPalette.AMBER_CRT to listOf(Color(0xFFFFAA00), Color(0xFF3D2900)),
        ColorPalette.GRAYSCALE to listOf(Color.White, Color.Gray, Color.DarkGray),
        ColorPalette.MATRIX to listOf(Color(0xFF00FF41), Color(0xFF00CC33), Color(0xFF003300))
    )

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        palettes.forEach { palette ->
            val isSelected = palette == selected
            val colors = paletteColors[palette] ?: listOf(Color.Gray)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onSelect(palette) }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(colors)
                        )
                        .border(
                            width = if (isSelected) 2.dp else 0.5.dp,
                            color = if (isSelected) ASCIIColors.Amber else ASCIIColors.TextMuted,
                            shape = RoundedCornerShape(10.dp)
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = palette.displayName.take(8),
                    color = if (isSelected) ASCIIColors.Amber else ASCIIColors.TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ASCIIColors.TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = subtitle,
                color = ASCIIColors.TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ASCIIColors.NeonGreen,
                checkedTrackColor = ASCIIColors.NeonGreenFaint,
                uncheckedThumbColor = ASCIIColors.TextMuted,
                uncheckedTrackColor = ASCIIColors.SurfaceElevated
            )
        )
    }
}
