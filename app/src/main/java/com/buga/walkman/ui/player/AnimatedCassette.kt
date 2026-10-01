package com.buga.walkman.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.rememberTextMeasurer
/**
 * Reusable animated compact audio cassette tape component.
 *
 * Modeled after the classic Sony UCX 46 cassette tape in horizontal landscape format,
 * featuring realistic rotating drive spools with teeth, dynamic magnetic tape transfer,
 * authentic waffle/knurled casing texture, and multi-tier label.
 *
 * @param modifier Modifier applied to the cassette container.
 * @param isPlaying Controls whether the cassette spools and tape are actively running.
 * @param rotationSpeed Multiplier factor for the rotation and tape travel speed (e.g. 1.0f = normal).
 * @param casingColor Background plastic housing color of the cassette shell.
 * @param labelAccentColor Primary accent color of the center label stripe (retro purple/magenta).
 * @param trackTitle Song/Track title displayed on the lined notepad section (e.g. "ADICTIVA").
 * @param artistText Artist or band name displayed on the notepad section (e.g. "ANUEL AA").
 * @param sideText Cassette side designation (e.g. "A" or "B").
 * @param modelText Cassette model designation (e.g. "UCX 46").
 */
@Composable
fun AnimatedCassette(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    rotationSpeed: Float = 1.0f,
    casingColor: Color = Color(0xFF222428),
    labelAccentColor: Color = Color(0xFF982594),
    trackTitle: String = "ADICTIVA",
    artistText: String = "ANUEL AA",
    sideText: String = "A",
    modelText: String = "UCX 46"
) {
    // Clamped speed factor to prevent division by zero or negative values
    val effectiveSpeed = rotationSpeed.coerceIn(0.1f, 5.0f)

    // Infinite transition coordinating all animation cycles
    val infiniteTransition = rememberInfiniteTransition(label = "CassetteReelTransition")

    // 1. Continuous rotation base angle (0 to 360 degrees)
    val baseRotationDuration = (2200 / effectiveSpeed).toInt().coerceAtLeast(100)
    val baseAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = baseRotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BaseRotationAngle"
    )

    // 2. Slow tape transfer progress (0.15f to 0.85f): tape transfers left to right
    val tapeProgressDuration = (32000 / effectiveSpeed).toInt().coerceAtLeast(1000)
    val tapeProgress by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = tapeProgressDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TapeProgress"
    )

    // 3. Continuous linear tape travel offset for moving tape striations
    val tapeTravelDuration = (900 / effectiveSpeed).toInt().coerceAtLeast(50)
    val tapeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = tapeTravelDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TapeMovementOffset"
    )

    // Physics-based angular speed variation:
    // Left spool (supply) rotates slightly faster as its tape diameter shrinks.
    // Right spool (take-up) rotates slightly slower as its tape diameter grows.
    val currentProgress = if (isPlaying) tapeProgress else 0.32f
    val leftReelAngle = if (isPlaying) (baseAngle * (0.90f + 0.25f * currentProgress)) % 360f else 15f
    val rightReelAngle = if (isPlaying) (baseAngle * (1.15f - 0.25f * currentProgress)) % 360f else 60f
    val currentTapeOffset = if (isPlaying) tapeOffset else 0f

    val animState = CassetteAnimationState(
        leftReelAngle = leftReelAngle,
        rightReelAngle = rightReelAngle,
        tapeProgress = currentProgress,
        tapeMovementOffset = currentTapeOffset
    )

    val style = CassetteStyle(
        casingColor = casingColor,
        casingHighlightColor = casingColor.copy(
            red = (casingColor.red * 1.35f).coerceAtMost(1f),
            green = (casingColor.green * 1.35f).coerceAtMost(1f),
            blue = (casingColor.blue * 1.35f).coerceAtMost(1f)
        ),
        labelAccentColor = labelAccentColor,
        trackTitle = trackTitle,
        artistText = artistText,
        sideText = sideText,
        modelText = modelText
    )

    val textMeasurer = rememberTextMeasurer()

    // Compact cassette standard aspect ratio: 100mm wide by 64mm high -> ~1.5625
    Box(
        modifier = modifier.aspectRatio(1.58f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            CassetteDrawing.drawCassette(
                drawScope = this,
                textMeasurer = textMeasurer,
                animationState = animState,
                style = style
            )
        }
    }
}

