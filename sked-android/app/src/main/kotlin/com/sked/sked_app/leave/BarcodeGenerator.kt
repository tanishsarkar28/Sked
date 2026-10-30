package com.sked.sked_app.leave

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure Kotlin Code 128 (Subset B) Barcode Encoder.
 *
 * Implements ISO/IEC 15417 specification without external dependencies.
 * Capable of rendering CODE128 barcodes readable by hardware laser turnstile scanners.
 */
object Code128Encoder {

    // 107 pattern definitions for Code 128 (0..106)
    // Each pattern has alternating bar and space widths (6 digits = 11 modules, except stop = 13 modules).
    private val PATTERNS = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    )

    private const val START_B = 104
    private const val STOP = 106
    private const val QUIET_ZONE_MODULES = 10

    /**
     * Encodes [text] into a boolean array of modules.
     * `true` = bar (black), `false` = space (white).
     * Includes quiet zones on both sides.
     */
    fun encode(text: String): BooleanArray {
        val clean = text.ifBlank { "0000" }
        val values = mutableListOf<Int>()
        values.add(START_B)

        for (ch in clean) {
            val code = (ch.code - 32).coerceIn(0, 95)
            values.add(code)
        }

        // Checksum calculation: (Start + sum(pos * code)) % 103
        var checksum = values[0]
        for (i in 1 until values.size) {
            checksum += i * values[i]
        }
        values.add(checksum % 103)
        values.add(STOP)

        // Expand patterns into modules
        val modules = mutableListOf<Boolean>()

        // Left Quiet Zone
        repeat(QUIET_ZONE_MODULES) { modules.add(false) }

        // Code symbols
        for (patternIndex in values) {
            val pat = PATTERNS.getOrElse(patternIndex) { PATTERNS[0] }
            var isBar = true
            for (charWidth in pat) {
                val width = charWidth.digitToIntOrNull() ?: 1
                repeat(width) { modules.add(isBar) }
                isBar = !isBar
            }
        }

        // Right Quiet Zone
        repeat(QUIET_ZONE_MODULES) { modules.add(false) }

        return modules.toBooleanArray()
    }
}

/**
 * High-contrast, sharp CODE128 Barcode Canvas.
 */
@Composable
fun Code128Barcode(
    code: String,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    barColor: Color = Color.Black,
    backgroundColor: Color = Color.White
) {
    val modules = remember(code) { Code128Encoder.encode(code) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalModules = modules.size
            if (totalModules == 0) return@Canvas

            val moduleWidth = size.width / totalModules.toFloat()
            val h = size.height

            for (i in modules.indices) {
                if (modules[i]) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x = i * moduleWidth, y = 0f),
                        size = Size(width = moduleWidth + 0.5f, height = h)
                    )
                }
            }
        }
    }
}
