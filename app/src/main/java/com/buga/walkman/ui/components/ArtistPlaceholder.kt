package com.buga.walkman.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch

private val ExpressivePalette = listOf(
    0xFF8E7BFF.toInt(), // violet
    0xFF6E9BFF.toInt(), // blue
    0xFF5CC8FF.toInt(), // cyan
    0xFF56D0B8.toInt(), // teal
    0xFF87C660.toInt(), // green
    0xFFC8C64A.toInt(), // lime
    0xFFFFB75E.toInt(), // amber
    0xFFFF9760.toInt(), // orange
    0xFFFF7770.toInt(), // coral
    0xFFEF7BC4.toInt()  // magenta
)

private fun expressiveColorFromText(text: String): Int {
    val seed = text.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return ExpressivePalette[seed % ExpressivePalette.size]
}

internal fun expressiveArtistColor(name: String): Int =
    expressiveColorFromText(name.ifBlank { "?" })

@Composable
fun ArtistPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val text = title.ifBlank { "?" }
    val baseArgb = remember(text) { expressiveArtistColor(text) }
    val endArgb = remember(baseArgb) { gawTint(baseArgb) }
    val (first, second) = remember(text) { gawInitials(text) }

    val scale = remember(text) { Animatable(0.82f) }
    val alpha = remember(text) { Animatable(0f) }

    LaunchedEffect(text) {
        launch { alpha.animateTo(1f, tween(durationMillis = 220)) }
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
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