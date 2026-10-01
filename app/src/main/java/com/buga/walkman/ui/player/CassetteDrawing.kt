package com.buga.walkman.ui.player

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-fidelity drawing logic for the classic audio cassette tape in horizontal orientation,
 * modeled precisely after the iconic Sony UCX 46 reference image.
 */
object CassetteDrawing {

    fun drawCassette(
        drawScope: DrawScope,
        textMeasurer: TextMeasurer,
        animationState: CassetteAnimationState,
        style: CassetteStyle
    ) {
        val w = drawScope.size.width
        val h = drawScope.size.height

        // 1. Outer dark matte casing with soft drop shadow & rounded corners
        drawCasingBase(drawScope, w, h, style)

        // 2. Micro-waffle / knurled grid texture across the casing surface
        drawWaffleTexture(drawScope, w, h)

        // 3. Embossed icons (arrow top-left, "SONY" top-right) and Phillips screws
        drawCasingEmbossingAndScrews(drawScope, w, h)

        // 4. Bottom tape head trapezoid assembly
        drawBottomTrapezoid(drawScope, w, h)

        // 5. Central multi-tier label (Top notes, Purple metallic band, Bottom silver titanium)
        drawCentralLabel(drawScope, textMeasurer, w, h, style)

        // 6. Cassette cutout window, spinning reels, wound magnetic tape, and gauge ruler
        drawWindowAndReels(drawScope, w, h, animationState, style)

        // 7. Glass specular reflection across the window
        drawGlassReflection(drawScope, w, h)
    }

    private fun drawCasingBase(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        style: CassetteStyle
    ) {
        val cornerRadius = w * 0.024f

        // Outer soft drop shadow
        drawScope.drawRoundRect(
            color = Color(0x77000000),
            topLeft = Offset(w * 0.008f, h * 0.015f),
            size = Size(w * 0.984f, h * 0.985f),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )

        // Casing gradient (subtle matte plastic sheen from top-left to bottom-right)
        val casingBrush = Brush.linearGradient(
            colors = listOf(
                style.casingHighlightColor,
                style.casingColor,
                style.casingColor,
                Color(0xFF141518)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        drawScope.drawRoundRect(
            brush = casingBrush,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )

        // Outer rim highlight bevel
        drawScope.drawRoundRect(
            color = Color(0x35FFFFFF),
            topLeft = Offset(1.5f, 1.5f),
            size = Size(w - 3f, h - 3f),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = 1.8f)
        )
    }

    private fun drawWaffleTexture(
        drawScope: DrawScope,
        w: Float,
        h: Float
    ) {
        // Label bounding box where texture should NOT be drawn
        val labelRect = Rect(w * 0.092f, h * 0.090f, w * 0.875f, h * 0.665f)
        // Bottom trapezoid bounds
        val trapRect = Rect(w * 0.17f, h * 0.68f, w * 0.79f, h * 0.96f)

        val stepX = w * 0.013f
        val stepY = h * 0.021f
        val tileSize = w * 0.0095f

        var curY = h * 0.035f
        while (curY < h * 0.96f) {
            var curX = w * 0.035f
            while (curX < w * 0.965f) {
                val inLabel = curX in labelRect.left..labelRect.right && curY in labelRect.top..labelRect.bottom
                val inTrap = curX in trapRect.left..trapRect.right && curY in trapRect.top..trapRect.bottom

                if (!inLabel && !inTrap) {
                    // Dark tile base
                    drawScope.drawRect(
                        color = Color(0x33000000),
                        topLeft = Offset(curX + 0.8f, curY + 0.8f),
                        size = Size(tileSize, tileSize)
                    )
                    // Highlight top-left edge of each tile for 3D knurled feel
                    drawScope.drawRect(
                        color = Color(0x18FFFFFF),
                        topLeft = Offset(curX, curY),
                        size = Size(tileSize * 0.7f, tileSize * 0.7f)
                    )
                }
                curX += stepX
            }
            curY += stepY
        }
    }

    private fun drawCasingEmbossingAndScrews(
        drawScope: DrawScope,
        w: Float,
        h: Float
    ) {
        // 1. Embossed triangular arrow at top-left
        val arrowX = w * 0.132f
        val arrowY = h * 0.052f
        val arrowSize = w * 0.018f

        val arrowPath = Path().apply {
            moveTo(arrowX, arrowY - arrowSize)
            lineTo(arrowX - arrowSize * 0.9f, arrowY + arrowSize * 0.7f)
            lineTo(arrowX + arrowSize * 0.9f, arrowY + arrowSize * 0.7f)
            close()
        }
        // Embossed shadow & highlight
        drawScope.drawPath(path = arrowPath, color = Color(0xFF0F1013), style = Fill)
        drawScope.drawPath(path = arrowPath, color = Color(0x33FFFFFF), style = Stroke(width = 1.2f))

        // 3. Five assembly screws (4 corners + 1 center bottom)
        val screwRadius = w * 0.0155f
        val screwPositions = listOf(
            Offset(w * 0.068f, h * 0.055f),
            Offset(w * 0.892f, h * 0.055f),
            Offset(w * 0.062f, h * 0.898f),
            Offset(w * 0.898f, h * 0.898f),
            Offset(w * 0.475f, h * 0.792f) // Center screw inside trapezoid
        )

        for (pos in screwPositions) {
            drawPhillipsScrew(drawScope, pos, screwRadius)
        }
    }

    private fun drawPhillipsScrew(drawScope: DrawScope, center: Offset, radius: Float) {
        // Deep recessed cavity
        drawScope.drawCircle(
            color = Color(0xFF0A0B0D),
            radius = radius * 1.18f,
            center = center
        )
        // Metallic screw head
        drawScope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF5E646C), Color(0xFF383B41), Color(0xFF1F2124)),
                center = center - Offset(radius * 0.25f, radius * 0.25f),
                radius = radius
            ),
            radius = radius,
            center = center
        )
        // Cross / Phillips slot
        val slotWidth = radius * 0.32f
        val slotLen = radius * 0.75f
        drawScope.drawLine(
            color = Color(0xFF0F1012),
            start = Offset(center.x - slotLen, center.y),
            end = Offset(center.x + slotLen, center.y),
            strokeWidth = slotWidth,
            cap = StrokeCap.Round
        )
        drawScope.drawLine(
            color = Color(0xFF0F1012),
            start = Offset(center.x, center.y - slotLen),
            end = Offset(center.x, center.y + slotLen),
            strokeWidth = slotWidth,
            cap = StrokeCap.Round
        )
    }

    private fun drawBottomTrapezoid(
        drawScope: DrawScope,
        w: Float,
        h: Float
    ) {
        val trapTop = h * 0.690f
        val trapBottom = h * 0.955f
        val trapTopLeft = w * 0.198f
        val trapTopRight = w * 0.760f
        val trapBottomLeft = w * 0.168f
        val trapBottomRight = w * 0.788f

        val trapPath = Path().apply {
            moveTo(trapTopLeft, trapTop)
            lineTo(trapTopRight, trapTop)
            lineTo(trapBottomRight, trapBottom)
            lineTo(trapBottomLeft, trapBottom)
            close()
        }

        // Drop shadow & beveled body
        drawScope.drawPath(path = trapPath, color = Color(0xFF181A1D))

        // Rim highlight stroke
        drawScope.drawPath(
            path = trapPath,
            color = Color(0x28FFFFFF),
            style = Stroke(width = 1.4f)
        )

        // Knurled grid inside the trapezoid
        val stepX = w * 0.013f
        val stepY = h * 0.021f
        val tileSize = w * 0.009f
        var ty = trapTop + h * 0.02f
        while (ty < trapBottom - h * 0.02f) {
            var tx = trapBottomLeft + w * 0.04f
            while (tx < trapBottomRight - w * 0.04f) {
                drawScope.drawRect(
                    color = Color(0x2A000000),
                    topLeft = Offset(tx + 0.6f, ty + 0.6f),
                    size = Size(tileSize, tileSize)
                )
                drawScope.drawRect(
                    color = Color(0x15FFFFFF),
                    topLeft = Offset(tx, ty),
                    size = Size(tileSize * 0.7f, tileSize * 0.7f)
                )
                tx += stepX
            }
            ty += stepY
        }

        // Tape head opening holes in the trapezoid:
        // 2 outer guide holes
        val outerHoleRadius = w * 0.021f
        drawScope.drawCircle(color = Color(0xFF090A0C), radius = outerHoleRadius, center = Offset(w * 0.255f, h * 0.885f))
        drawScope.drawCircle(color = Color(0x33FFFFFF), radius = outerHoleRadius, center = Offset(w * 0.255f, h * 0.885f), style = Stroke(1f))
        drawScope.drawCircle(color = Color(0xFF090A0C), radius = outerHoleRadius, center = Offset(w * 0.702f, h * 0.885f))
        drawScope.drawCircle(color = Color(0x33FFFFFF), radius = outerHoleRadius, center = Offset(w * 0.702f, h * 0.885f), style = Stroke(1f))

        // 2 inner alignment holes
        val innerHoleRadius = w * 0.016f
        drawScope.drawCircle(color = Color(0xFF090A0C), radius = innerHoleRadius, center = Offset(w * 0.358f, h * 0.875f))
        drawScope.drawCircle(color = Color(0x33FFFFFF), radius = innerHoleRadius, center = Offset(w * 0.358f, h * 0.875f), style = Stroke(1f))
        drawScope.drawCircle(color = Color(0xFF090A0C), radius = innerHoleRadius, center = Offset(w * 0.598f, h * 0.875f))
        drawScope.drawCircle(color = Color(0x33FFFFFF), radius = innerHoleRadius, center = Offset(w * 0.598f, h * 0.875f), style = Stroke(1f))

        // 2 tiny bottom pin notches
        val pinRadius = w * 0.009f
        drawScope.drawCircle(color = Color(0xFF090A0C), radius = pinRadius, center = Offset(w * 0.218f, h * 0.940f))
        drawScope.drawCircle(color = Color(0xFF090A0C), radius = pinRadius, center = Offset(w * 0.738f, h * 0.940f))
    }

    private fun drawCentralLabel(
        drawScope: DrawScope,
        textMeasurer: TextMeasurer,
        w: Float,
        h: Float,
        style: CassetteStyle
    ) {
        val labelLeft = w * 0.096f
        val labelTop = h * 0.092f
        val labelWidth = w * 0.770f
        val labelHeight = h * 0.572f
        val corner = w * 0.018f

        val labelPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight),
                    cornerRadius = CornerRadius(corner, corner)
                )
            )
        }

        drawScope.clipPath(labelPath) {
            // Label section heights:
            // Top white notepad area: 36%
            // Middle purple metallic band: 44%
            // Bottom silver titanium strip: 20%
            val topH = labelHeight * 0.355f
            val midH = labelHeight * 0.445f
            val botH = labelHeight - topH - midH

            val midY = labelTop + topH
            val botY = midY + midH

            // -------------------------------------------------------------
            // SECTION 1: TOP WHITE NOTEPAD LABEL (track & artist lines)
            // -------------------------------------------------------------
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        style.labelLightColor,
                        style.labelLightColor.copy(red = 0.95f, green = 0.95f, blue = 0.93f)
                    ),
                    startY = labelTop,
                    endY = labelTop + topH
                ),
                topLeft = Offset(labelLeft, labelTop),
                size = Size(labelWidth, topH)
            )

            // Ruled handwriting lines
            val numLines = 3
            val lineSpacing = topH / (numLines + 0.75f)
            for (i in 1..numLines) {
                val ly = labelTop + i * lineSpacing
                drawLine(
                    color = Color(0x35808080),
                    start = Offset(labelLeft + w * 0.015f, ly),
                    end = Offset(labelLeft + labelWidth - w * 0.015f, ly),
                    strokeWidth = 1f
                )
            }

            // Track title text, aligned on the first ruled line
            val titleLayout = textMeasurer.measure(
                text = style.trackTitle,
                style = TextStyle(
                    color = Color(0xFF181A1D),
                    fontSize = (w * 0.012f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default,
                    letterSpacing = 1.sp
                )
            )
            drawText(
                textLayoutResult = titleLayout,
                topLeft = Offset(
                    labelLeft + w * 0.019f,
                    labelTop + lineSpacing * 2f - titleLayout.size.height * 0.85f
                )
            )

            // Artist text, aligned on the second ruled line
            val artistLayout = textMeasurer.measure(
                text = style.artistText,
                style = TextStyle(
                    color = Color(0xFF181A1D),
                    fontSize = (w * 0.011f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Default,
                    letterSpacing = 1.sp
                )
            )
            drawText(
                textLayoutResult = artistLayout,
                topLeft = Offset(
                    labelLeft + w * 0.019f,
                    labelTop + lineSpacing * 3f - artistLayout.size.height * 0.85f
                )
            )

            // -------------------------------------------------------------
            // SECTION 2: MIDDLE BRUSHED METALLIC PURPLE/MAGENTA STRIPE
            // -------------------------------------------------------------
            val purpleGradient = Brush.verticalGradient(
                colors = listOf(
                    style.labelAccentColor,
                    style.labelAccentColor.copy(red = (style.labelAccentColor.red * 1.15f).coerceAtMost(1f)),
                    style.labelAccentColor,
                    style.labelAccentColor.copy(red = (style.labelAccentColor.red * 0.85f))
                ),
                startY = midY,
                endY = midY + midH
            )
            drawRect(
                brush = purpleGradient,
                topLeft = Offset(labelLeft, midY),
                size = Size(labelWidth, midH)
            )

            // Horizontal brushed metallic lines across the purple band
            var px = labelLeft
            while (px < labelLeft + labelWidth) {
                val alpha = if ((px.toInt() % 8) == 0) 0.16f else 0.06f
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(px, midY),
                    end = Offset(px, midY + midH),
                    strokeWidth = 1f
                )
                px += 3.5f
            }

            // Top and bottom thin silver accent pin-stripes on the purple band
            drawLine(
                color = Color(0xE0FFFFFF),
                start = Offset(labelLeft, midY + 1.5f),
                end = Offset(labelLeft + labelWidth, midY + 1.5f),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color(0xE0FFFFFF),
                start = Offset(labelLeft, midY + midH - 1.5f),
                end = Offset(labelLeft + labelWidth, midY + midH - 1.5f),
                strokeWidth = 1.5f
            )

            // Side letter "A" on the left of the purple band
            val sideLetterLayout = textMeasurer.measure(
                text = style.sideText,
                style = TextStyle(
                    color = Color(0xFF320536),
                    fontSize = (w * 0.022f).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Default
                )
            )
            drawText(
                textLayoutResult = sideLetterLayout,
                topLeft = Offset(
                    labelLeft + w * 0.022f,
                    midY + (midH - sideLetterLayout.size.height) / 2f
                )
            )

            // -------------------------------------------------------------
            // SECTION 3: BOTTOM BRUSHED SILVER / TITANIUM STRIPE
            // -------------------------------------------------------------
            val silverGradient = Brush.verticalGradient(
                colors = listOf(
                    style.labelMetallicColor.copy(red = 0.88f, green = 0.90f, blue = 0.93f),
                    style.labelMetallicColor,
                    style.labelMetallicColor.copy(red = 0.74f, green = 0.76f, blue = 0.80f)
                ),
                startY = botY,
                endY = botY + botH
            )
            drawRect(
                brush = silverGradient,
                topLeft = Offset(labelLeft, botY),
                size = Size(labelWidth, botH)
            )

            // Fine vertical grain on silver stripe
            var sx = labelLeft
            while (sx < labelLeft + labelWidth) {
                val sAlpha = if ((sx.toInt() % 7) == 0) 0.12f else 0.04f
                drawLine(
                    color = Color.Black.copy(alpha = sAlpha),
                    start = Offset(sx, botY),
                    end = Offset(sx, botY + botH),
                    strokeWidth = 1f
                )
                sx += 3f
            }
        }

        // Thin dark border line around label
        drawScope.drawRoundRect(
            color = Color(0x55000000),
            topLeft = Offset(labelLeft, labelTop),
            size = Size(labelWidth, labelHeight),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 1.5f)
        )
    }

    private fun drawWindowAndReels(
        drawScope: DrawScope,
        w: Float,
        h: Float,
        animationState: CassetteAnimationState,
        style: CassetteStyle
    ) {
        // The central window cavity cut into the cassette and purple label
        val winLeft = w * 0.170f
        val winTop = h * 0.315f
        val winWidth = w * 0.625f
        val winHeight = h * 0.245f
        val winCorner = w * 0.016f

        // Outer beveled frame of window
        drawScope.drawRoundRect(
            color = Color(0xFF0F1012),
            topLeft = Offset(winLeft - 2f, winTop - 2f),
            size = Size(winWidth + 4f, winHeight + 4f),
            cornerRadius = CornerRadius(winCorner + 1.5f, winCorner + 1.5f)
        )
        drawScope.drawRoundRect(
            color = Color(0x30FFFFFF),
            topLeft = Offset(winLeft, winTop),
            size = Size(winWidth, winHeight),
            cornerRadius = CornerRadius(winCorner, winCorner),
            style = Stroke(width = 1.4f)
        )

        // Window background: tinted dark acrylic
        drawScope.drawRoundRect(
            color = Color(0xFF16181B),
            topLeft = Offset(winLeft, winTop),
            size = Size(winWidth, winHeight),
            cornerRadius = CornerRadius(winCorner, winCorner)
        )

        val leftReelCenter = Offset(w * 0.298f, winTop + winHeight / 2f)
        val rightReelCenter = Offset(w * 0.668f, winTop + winHeight / 2f)

        val hubRadius = w * 0.065f
        val minTapeRadius = hubRadius + w * 0.008f
        val maxTapeRadius = w * 0.118f

        // Dynamic tape supply calculation (tape moves left -> right)
        val progress = animationState.tapeProgress.coerceIn(0f, 1f)
        val leftTapeRadius = minTapeRadius + (maxTapeRadius - minTapeRadius) * (1f - progress)
        val rightTapeRadius = minTapeRadius + (maxTapeRadius - minTapeRadius) * progress

        val windowPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(winLeft, winTop, winLeft + winWidth, winTop + winHeight),
                    cornerRadius = CornerRadius(winCorner, winCorner)
                )
            )
        }

        drawScope.clipPath(windowPath) {
            // 1. Dark wound magnetic tape on spools
            drawTapeRoll(this, leftReelCenter, leftTapeRadius, style)
            drawTapeRoll(this, rightReelCenter, rightTapeRadius, style)

            // 2. Horizontal moving tape bridge along bottom of window
            drawConnectingTape(this, w,
                leftReelCenter, rightReelCenter, animationState.tapeMovementOffset, style)

            // 3. Central transparent glass window between spools with tape ruler
            val gaugeLeft = w * 0.375f
            val gaugeRight = w * 0.590f
            val gaugeTop = winTop + 4f
            val gaugeBottom = winTop + winHeight - 4f

            // Inner glass area
            drawRoundRect(
                color = Color(0x880C0D10),
                topLeft = Offset(gaugeLeft, gaugeTop),
                size = Size(gaugeRight - gaugeLeft, gaugeBottom - gaugeTop),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = Color(0x35FFFFFF),
                topLeft = Offset(gaugeLeft, gaugeTop),
                size = Size(gaugeRight - gaugeLeft, gaugeBottom - gaugeTop),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1f)
            )

            // Curved tape guide silhouettes behind glass
            val guidePath = Path().apply {
                moveTo(gaugeLeft, gaugeTop)
                cubicTo(
                    gaugeLeft + w * 0.025f, gaugeTop + (gaugeBottom - gaugeTop) * 0.5f,
                    gaugeLeft + w * 0.025f, gaugeTop + (gaugeBottom - gaugeTop) * 0.5f,
                    gaugeLeft, gaugeBottom
                )
                moveTo(gaugeRight, gaugeTop)
                cubicTo(
                    gaugeRight - w * 0.025f, gaugeTop + (gaugeBottom - gaugeTop) * 0.5f,
                    gaugeRight - w * 0.025f, gaugeTop + (gaugeBottom - gaugeTop) * 0.5f,
                    gaugeRight, gaugeBottom
                )
            }
            drawPath(path = guidePath, color = Color(0x38FFFFFF), style = Stroke(1.2f))

            // Measurement ruler on glass
            val midY = (gaugeTop + gaugeBottom) / 2f
            // Central horizontal axis line
            drawLine(
                color = Color(0x88FFFFFF),
                start = Offset(gaugeLeft + w * 0.015f, midY),
                end = Offset(gaugeRight - w * 0.015f, midY),
                strokeWidth = 1.2f
            )

            // Vertical tick marks
            val numTicks = 9
            val tickStep = (gaugeRight - gaugeLeft - w * 0.035f) / (numTicks - 1)
            for (i in 0 until numTicks) {
                val tx = gaugeLeft + w * 0.0175f + i * tickStep
                val isCenter = (i == numTicks / 2)
                val tickH = if (isCenter) (gaugeBottom - gaugeTop) * 0.52f else (gaugeBottom - gaugeTop) * 0.26f

                drawLine(
                    color = if (isCenter) Color(0xDDFFFFFF) else Color(0x88FFFFFF),
                    start = Offset(tx, midY - tickH / 2f),
                    end = Offset(tx, midY + tickH / 2f),
                    strokeWidth = if (isCenter) 1.8f else 1.1f
                )
            }

            // 4. White plastic spoked hubs with teeth
            drawSpoolHub(this, leftReelCenter, hubRadius, animationState.leftReelAngle, style)
            drawSpoolHub(this, rightReelCenter, hubRadius, animationState.rightReelAngle, style)
        }
    }

    private fun drawTapeRoll(
        drawScope: DrawScope,
        center: Offset,
        radius: Float,
        style: CassetteStyle
    ) {
        // Base dark magnetic tape fill
        drawScope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    style.tapeColor,
                    Color(0xFF281C16),
                    style.tapeColor,
                    Color(0xFF100C09)
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // Concentric winding rings
        val numRings = 4
        for (i in 1..numRings) {
            val ringRadius = radius * (0.62f + 0.35f * (i.toFloat() / numRings))
            drawScope.drawCircle(
                color = Color(0x22000000),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1f)
            )
        }
    }

    private fun drawConnectingTape(
        drawScope: DrawScope,
        w: Float,
        leftCenter: Offset,
        rightCenter: Offset,
        tapeOffset: Float,
        style: CassetteStyle
    ) {
        // Horizontal tape strip running across bottom of window
        val tapeY = leftCenter.y + w * 0.075f
        val tapeHeight = w * 0.016f
        val startX = leftCenter.x
        val endX = rightCenter.x

        drawScope.drawRect(
            color = style.tapeColor,
            topLeft = Offset(startX, tapeY),
            size = Size(endX - startX, tapeHeight)
        )

        // Moving striations on the tape to show active physical transport
        val segW = 14f
        var curX = startX + (tapeOffset % segW)
        while (curX < endX) {
            drawScope.drawLine(
                color = Color(0x22FFFFFF),
                start = Offset(curX, tapeY),
                end = Offset(curX, tapeY + tapeHeight),
                strokeWidth = 1.2f
            )
            curX += segW
        }
    }

    private fun drawSpoolHub(
        drawScope: DrawScope,
        center: Offset,
        radius: Float,
        rotationAngle: Float,
        style: CassetteStyle
    ) {
        // Outer ring (light off-white plastic)
        drawScope.drawCircle(
            color = style.spoolColor,
            radius = radius,
            center = center
        )
        drawScope.drawCircle(
            color = Color(0x2A000000),
            radius = radius,
            center = center,
            style = Stroke(width = 1.2f)
        )

        // Rotating gear teeth & inner hub drive notches
        drawScope.rotate(degrees = rotationAngle, pivot = center) {
            val numTeeth = 6
            val angleStep = 360f / numTeeth
            val toothInnerRadius = radius * 0.58f
            val toothOuterRadius = radius * 0.94f
            val toothWidth = radius * 0.26f

            // 6 Outer perimeter drive teeth
            for (i in 0 until numTeeth) {
                val rad = Math.toRadians((i * angleStep).toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()

                val p1 = Offset(center.x + toothInnerRadius * cosA, center.y + toothInnerRadius * sinA)
                val p2 = Offset(center.x + toothOuterRadius * cosA, center.y + toothOuterRadius * sinA)

                drawLine(
                    color = Color(0xFFB0B6BE),
                    start = p1,
                    end = p2,
                    strokeWidth = toothWidth,
                    cap = StrokeCap.Round
                )
            }

            // Dark inner drive well
            val wellRadius = radius * 0.54f
            drawCircle(
                color = Color(0xFF121417),
                radius = wellRadius,
                center = center
            )

            // Hollow drive hole
            val holeRadius = radius * 0.36f
            drawCircle(
                color = Color(0xFF07080A),
                radius = holeRadius,
                center = center
            )

            // 3 Inner drive splines/keys
            val splineStep = 120f
            for (j in 0 until 3) {
                val sRad = Math.toRadians((j * splineStep).toDouble())
                val sCos = cos(sRad).toFloat()
                val sSin = sin(sRad).toFloat()

                val sStart = Offset(center.x + holeRadius * 0.38f * sCos, center.y + holeRadius * 0.38f * sSin)
                val sEnd = Offset(center.x + holeRadius * sCos, center.y + holeRadius * sSin)

                drawLine(
                    color = style.spoolColor,
                    start = sStart,
                    end = sEnd,
                    strokeWidth = radius * 0.15f,
                    cap = StrokeCap.Square
                )
            }

            // Red tape clamp anchor notch
            val clampAngleRad = Math.toRadians(40.0)
            val clampCenter = Offset(
                center.x + radius * 0.74f * cos(clampAngleRad).toFloat(),
                center.y + radius * 0.74f * sin(clampAngleRad).toFloat()
            )
            drawCircle(
                color = Color(0xFFE53935),
                radius = radius * 0.11f,
                center = clampCenter
            )
        }
    }

    private fun drawGlassReflection(
        drawScope: DrawScope,
        w: Float,
        h: Float
    ) {
        val winLeft = w * 0.170f
        val winTop = h * 0.315f
        val winWidth = w * 0.625f
        val winHeight = h * 0.245f

        // Diagonal glass reflection highlight across the window
        val glarePath = Path().apply {
            moveTo(winLeft + winWidth * 0.25f, winTop)
            lineTo(winLeft + winWidth * 0.45f, winTop)
            lineTo(winLeft + winWidth * 0.32f, winTop + winHeight)
            lineTo(winLeft + winWidth * 0.12f, winTop + winHeight)
            close()
        }

        drawScope.drawPath(
            path = glarePath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x00FFFFFF),
                    Color(0x1EFFFFFF),
                    Color(0x2AFFFFFF),
                    Color(0x00FFFFFF)
                ),
                start = Offset(winLeft + winWidth * 0.25f, winTop),
                end = Offset(winLeft + winWidth * 0.32f, winTop + winHeight)
            )
        )
    }
}
