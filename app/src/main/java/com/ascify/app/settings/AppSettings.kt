package com.ascify.app.settings

import androidx.compose.ui.graphics.Color

// ─── Enums ───────────────────────────────────────────────────────────────────

enum class CharacterSet(val displayName: String, val chars: String) {
    CLASSIC(
        "Classic ASCII",
        // Dense → light. Standard Paulhus ramp.
        "@%#*+=-:. "
    ),
    EXTENDED(
        "Extended ASCII",
        "@\$B%8&WM#*oahkbdpqwmZO0QLCJUYXzcvunxrjft/\\|()1{}[]?-_+~<>i!lI;:,\"^`'. "
    ),
    BLOCKS(
        "Blocks",
        "█▓▒░ "
    ),
    BRAILLE(
        "Braille",
        "⣿⣷⣶⣤⣄⣀⠿⠷⠶⠤⠄⠀"
    ),
    MINIMAL(
        "Minimal",
        "@#+-. "
    ),
    MATRIX(
        "Matrix",
        "ｦｧｨｩｪｫｬｭｮｯｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789"
    );

    fun charForBrightness(brightness: Float): Char {
        // brightness in 0.0 (dark) .. 1.0 (bright)
        val inverted = 1f - brightness.coerceIn(0f, 1f)
        val index = (inverted * (chars.length - 1)).toInt().coerceIn(0, chars.length - 1)
        return chars[index]
    }
}

enum class ColorPalette(val displayName: String) {
    FULL_RGB("Full RGB"),
    GREEN_TERMINAL("Green Terminal"),
    AMBER_CRT("Amber CRT"),
    GRAYSCALE("Grayscale"),
    MATRIX("Matrix");

    fun mapColor(originalColor: Int): Int {
        val r = (originalColor shr 16) and 0xFF
        val g = (originalColor shr 8) and 0xFF
        val b = originalColor and 0xFF
        val brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

        return when (this) {
            FULL_RGB -> originalColor
            GREEN_TERMINAL -> {
                val intensity = (brightness * 255).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (0 shl 16) or (intensity shl 8) or 0
            }
            AMBER_CRT -> {
                val intensity = brightness
                val ar = (255 * intensity).toInt()
                val ag = (170 * intensity).toInt()
                val ab = 0
                (0xFF shl 24) or (ar.coerceIn(0,255) shl 16) or (ag.coerceIn(0,255) shl 8) or ab
            }
            GRAYSCALE -> {
                val gray = (brightness * 255).toInt().coerceIn(0, 255)
                (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            MATRIX -> {
                // Matrix: bright cells get bright green, dark get dark green
                val hi = (brightness * 255).toInt()
                val lo = (brightness * 80).toInt()
                (0xFF shl 24) or (0 shl 16) or (hi.coerceIn(0,255) shl 8) or lo.coerceIn(0,255)
            }
        }
    }
}

enum class FlashMode { OFF, ON, AUTO, TORCH }

enum class CameraLens(val displayName: String) {
    MAIN("1×"),
    ULTRAWIDE("0.6×"),
    TELEPHOTO("3×"),
    SELFIE("Front")
}

enum class CaptureMode { PHOTO, VIDEO }

enum class ExportFormat { PNG, JPG }

enum class AsciiDensity(val displayName: String, val columns: Int) {
    RETRO("Retro", 40),
    LOW("Low", 60),
    MEDIUM("Medium", 90),
    HIGH("High", 120),
    ULTRA("Ultra", 160)
}

// ─── Settings Data Class ──────────────────────────────────────────────────────

data class AppSettings(
    val characterSet: CharacterSet = CharacterSet.CLASSIC,
    val colorPalette: ColorPalette = ColorPalette.FULL_RGB,
    val asciiDensity: AsciiDensity = AsciiDensity.MEDIUM,
    val exportFormat: ExportFormat = ExportFormat.PNG,
    val saveOriginalFrame: Boolean = false,
    val edgeEnhancement: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val adaptiveRendering: Boolean = true,
    val flashMode: FlashMode = FlashMode.OFF,
    val selectedLens: CameraLens = CameraLens.MAIN,
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val fontSize: Float = 6f,  // px per character cell
    val showFpsCounter: Boolean = false
)
