package com.buga.walkman.ui.player

import androidx.compose.ui.graphics.Color

/**
 * Data model defining the visual theme and styling parameters for [AnimatedCassette].
 */
data class CassetteStyle(
    val casingColor: Color = Color(0xFF222428),
    val casingHighlightColor: Color = Color(0xFF2E3137),
    val labelAccentColor: Color = Color(0xFF982594),
    val labelLightColor: Color = Color(0xFFF3F2EE),
    val labelMetallicColor: Color = Color(0xFFC4C8CE),
    val spoolColor: Color = Color(0xFFE8ECEF),
    val tapeColor: Color = Color(0xFF1B1410),
    val trackTitle: String = "ADICTIVA",
    val artistText: String = "ANUEL AA",
    val sideText: String = "A",
    val modelText: String = "UCX 46",
    val subText: String = "TYPE II (CrO2) POSITION   HIGH BIAS 70µs EQ"
)

/**
 * State holding dynamic values for the cassette animation.
 *
 * @param leftReelAngle Rotation angle (in degrees) of the left (supply) reel.
 * @param rightReelAngle Rotation angle (in degrees) of the right (take-up) reel.
 * @param tapeProgress Ratio (0.0f to 1.0f) of tape transferred from left to right reel.
 * @param tapeMovementOffset Offset for animating tape texture/travel between spools.
 */
data class CassetteAnimationState(
    val leftReelAngle: Float = 0f,
    val rightReelAngle: Float = 0f,
    val tapeProgress: Float = 0.28f,
    val tapeMovementOffset: Float = 0f
)

