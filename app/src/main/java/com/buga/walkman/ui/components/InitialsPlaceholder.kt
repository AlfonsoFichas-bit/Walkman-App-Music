package com.buga.walkman.ui.components

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.min

private val PlaceholderColors = listOf(
    0xFF4185F3.toInt(),
    0xFF4185F3.toInt(),
    0xFFDB3333.toInt(),
    0xFFF99A1E.toInt(),
    0xFF7EAC3A.toInt(),
    0xFF4F6778.toInt()
)

private const val FIRST_LETTER_ALPHA = 46
private const val SECOND_LETTER_ALPHA = 28
private const val OVERLAY_RATIO = 0.5f

private fun colorFromText(text: String): Int {
    val seed = text.hashCode()
    return PlaceholderColors[((seed % PlaceholderColors.size) + PlaceholderColors.size) % PlaceholderColors.size]
}

internal fun gawTint(argb: Int): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = min(hsv[1] * 1.1f, 1f)
    hsv[2] = min(hsv[2] * 0.75f, 1f)
    return android.graphics.Color.HSVToColor(android.graphics.Color.alpha(argb), hsv)
}

internal fun gawInitials(text: String): Pair<Char?, Char?> {
    val words = text.trim().split(" ").map { it.trim() }.filter { it.isNotEmpty() }
    if (words.isEmpty()) return null to null
    val first = words[0].firstOrNull()?.uppercaseChar()
    return if (words.size == 1) {
        val word = words[0]
        first to (if (word.length > 1) word[1].uppercaseChar() else null)
    } else {
        first to words[1].firstOrNull()?.uppercaseChar()
    }
}

@Composable
fun InitialsPlaceholder(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val text = remember(title, subtitle) { title.ifBlank { subtitle } }
    val baseArgb = remember(text) { colorFromText(text) }
    val endArgb = remember(baseArgb) { gawTint(baseArgb) }
    val (first, second) = remember(text) { gawInitials(text) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color(baseArgb),
                        Color(endArgb)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        GawLetters(first, second, sizeFraction = 1.8f)
    }
}

@Composable
internal fun GawLetters(first: Char?, second: Char?, sizeFraction: Float = 1.8f) {
    val overlayXfermode = remember { PorterDuffXfermode(PorterDuff.Mode.ADD) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .rotate(20f)
    ) {
        fun drawLetter(ch: Char?, alpha: Int, xFrac: Float, yFrac: Float) {
            if (ch == null) return
            val unit = size.minDimension
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = unit * sizeFraction
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val letter = ch.toString()
            val x = xFrac * unit
            val y = yFrac * unit

            paint.color = android.graphics.Color.argb(
                min(255, (alpha * OVERLAY_RATIO).toInt()), 255, 255, 255
            )
            paint.xfermode = overlayXfermode
            drawContext.canvas.nativeCanvas.drawText(letter, x, y, paint)

            paint.xfermode = null
            drawContext.canvas.nativeCanvas.drawText(letter, x, y, paint)
        }

        drawLetter(first, FIRST_LETTER_ALPHA, -0.2f, 1.05f)
        drawLetter(second, SECOND_LETTER_ALPHA, 0.3f, 1.05f)
    }
}