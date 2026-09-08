package com.buga.walkman.data.media

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.buga.walkman.ui.theme.WalkmanAccentHighlight
import com.buga.walkman.ui.theme.WalkmanGradientBottom
import com.buga.walkman.ui.theme.WalkmanGradientTop
import kotlin.math.abs

data class PlayerGradientColors(
    val top: Color,
    val bottom: Color,
    val highlight: Color,
    val tertiary: Color
)

class ArtworkColorExtractor(private val context: Context) {

    private var lastAlbumId: Long = -1L
    private var lastColors: PlayerGradientColors? = null

    suspend fun colorsFor(albumId: Long, albumArtUri: Uri): PlayerGradientColors {
        lastColors?.takeIf { albumId == lastAlbumId }?.let { return it }
        val colors = extractArtworkColors(context, albumArtUri)
        lastAlbumId = albumId
        lastColors = colors
        return colors
    }
}

private suspend fun extractArtworkColors(context: Context, uri: Uri): PlayerGradientColors {
    val fallback = PlayerGradientColors(
        top = WalkmanGradientTop,
        bottom = WalkmanGradientBottom,
        highlight = WalkmanAccentHighlight,
        tertiary = WalkmanAccentHighlight
    )
    return try {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        if (result is SuccessResult) {
            val drawable = result.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
                ?: return fallback
            val palette = Palette.from(bitmap).generate()

            val vibrantInt = palette.getDarkVibrantColor(0)
            val mutedInt = palette.getDarkMutedColor(0)
            val dominantInt = palette.getDominantColor(0)
            val vibrantFallback = palette.getVibrantColor(0)
            val mutedFallback = palette.getMutedColor(0)
            val lightVibrantInt = palette.getLightVibrantColor(0)
            val lightMutedInt = palette.getLightMutedColor(0)

            val topRaw = when {
                vibrantInt != 0 -> vibrantInt
                dominantInt != 0 -> dominantInt
                vibrantFallback != 0 -> vibrantFallback
                mutedInt != 0 -> mutedInt
                else -> 0
            }

            val bottomRaw = when {
                mutedInt != 0 -> mutedInt
                dominantInt != 0 -> dominantInt
                mutedFallback != 0 -> mutedFallback
                vibrantInt != 0 -> vibrantInt
                else -> 0
            }

            val highlightRaw = when {
                lightVibrantInt != 0 -> lightVibrantInt
                vibrantFallback != 0 -> vibrantFallback
                lightMutedInt != 0 -> lightMutedInt
                dominantInt != 0 -> dominantInt
                else -> 0
            }

            val tertiaryRaw = when {
                vibrantFallback != 0 -> vibrantFallback
                mutedFallback != 0 -> mutedFallback
                vibrantInt != 0 -> vibrantInt
                mutedInt != 0 -> mutedInt
                else -> 0
            }

            if (topRaw == 0 && bottomRaw == 0 && highlightRaw == 0 && tertiaryRaw == 0) {
                return fallback
            }

            val topColor = if (topRaw != 0) {
                Color(topRaw).ensureContrast(WalkmanGradientBottom, minDiff = 0.18f)
            } else {
                WalkmanGradientTop
            }
            val bottomColor = if (bottomRaw != 0) {
                Color(bottomRaw).ensureContrast(WalkmanGradientBottom, minDiff = 0.12f)
            } else {
                WalkmanGradientBottom
            }
            val highlightColor = if (highlightRaw != 0) {
                Color(highlightRaw).ensureHighlight()
            } else {
                WalkmanAccentHighlight
            }
            val tertiaryColor = if (tertiaryRaw != 0) {
                Color(tertiaryRaw).ensureContrast(WalkmanGradientBottom, minDiff = 0.20f)
            } else {
                WalkmanAccentHighlight
            }

            val top = lerp(WalkmanGradientTop, topColor, 0.7f)
            val bottom = lerp(WalkmanGradientBottom, bottomColor, 0.7f)

            PlayerGradientColors(top = top, bottom = bottom, highlight = highlightColor, tertiary = tertiaryColor)
        } else {
            fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

private fun Color.ensureContrast(background: Color, minDiff: Float): Color {
    val hsl = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsl)

    val bgHsl = FloatArray(3)
    android.graphics.Color.colorToHSV(background.toArgb(), bgHsl)

    val currentLightness = hsl[2]
    val bgLightness = bgHsl[2]
    val diff = abs(currentLightness - bgLightness)

    if (diff >= minDiff) {
        return this
    }

    if (currentLightness <= bgLightness) {
        hsl[2] = (bgLightness + minDiff).coerceAtMost(1f)
    } else {
        hsl[2] = (bgLightness - minDiff).coerceAtLeast(0f)
    }

    if (hsl[1] < 0.25f) {
        hsl[1] = 0.35f
    }

    return Color(android.graphics.Color.HSVToColor(hsl))
}

private fun Color.ensureHighlight(): Color {
    val hsl = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsl)

    if (hsl[2] < 0.6f) {
        hsl[2] = 0.68f
    }

    if (hsl[1] < 0.45f) {
        hsl[1] = 0.55f
    }

    return Color(android.graphics.Color.HSVToColor(hsl))
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val a = start
    val b = stop
    return Color(
        red = a.red + (b.red - a.red) * fraction,
        green = a.green + (b.green - a.green) * fraction,
        blue = a.blue + (b.blue - a.blue) * fraction,
        alpha = a.alpha + (b.alpha - a.alpha) * fraction
    )
}