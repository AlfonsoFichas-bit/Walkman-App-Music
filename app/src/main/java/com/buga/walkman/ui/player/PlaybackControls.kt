package com.buga.walkman.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buga.walkman.R
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

@Composable
internal fun PlaybackControlsSection(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onSeek: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.primary,
    sliderPosition: Float? = null,
    onSliderPositionChange: (Float?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TransportButtons(
            isPlaying = isPlaying,
            onPrev = onPrev,
            onNext = onNext,
            onPlayPause = onPlayPause,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            accentColor = accentColor,
            tertiaryColor = tertiaryColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        SongProgressBar(
            positionMs = positionMs,
            durationMs = durationMs,
            sliderPosition = sliderPosition,
            onSliderPositionChange = onSliderPositionChange,
            onSeek = onSeek,
            accentColor = accentColor
        )
    }
}

@Composable
internal fun TransportButtons(
    isPlaying: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    tertiaryColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    var flipRotation by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(50.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportIconButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.cd_previous),
                onClick = onPrev
            )

            FilledIconButton(
                onClick = {
                    flipRotation = if (flipRotation < 90f) 180f else 0f
                    onPlayPause()
                },
                modifier = Modifier
                    .size(72.dp)
                    .border(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = 0.4f),
                        shape = CircleShape
                    ),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accentColor,
                    contentColor = Color.White
                )
            ) {
                val playPauseRotation by animateFloatAsState(
                    targetValue = flipRotation,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "playPauseRotation"
                )

                val iconScale = abs(cos(playPauseRotation * PI / 180.0)).toFloat()
                    .coerceIn(0.0f, 1.0f)
                val flipX = if (playPauseRotation > 90f) -1f else 1f

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            this.rotationY = playPauseRotation
                            cameraDistance = 12f * density
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = stringResource(
                            if (isPlaying) R.string.cd_pause else R.string.cd_play
                        ),
                        modifier = Modifier.size(36.dp).graphicsLayer {
                            scaleX = iconScale * flipX
                            scaleY = iconScale
                        }
                    )
                }
            }

            TransportIconButton(
                icon = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.cd_next),
                onClick = onNext
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TransportIconButton(
                icon = Icons.Default.Shuffle,
                contentDescription = stringResource(R.string.cd_shuffle),
                onClick = onToggleShuffle,
                isActive = shuffleEnabled,
                activeColor = tertiaryColor,
                iconSize = 22.dp,
                showBorder = false
            )

            TransportIconButton(
                icon = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                    Icons.Default.RepeatOne
                } else {
                    Icons.Default.Repeat
                },
                contentDescription = stringResource(R.string.cd_repeat),
                onClick = onCycleRepeat,
                isActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                activeColor = tertiaryColor,
                iconSize = 22.dp,
                showBorder = false
            )
        }
    }
}

@Composable
internal fun SongProgressBar(
    positionMs: Long,
    durationMs: Long,
    sliderPosition: Float? = null,
    onSliderPositionChange: (Float?) -> Unit = {},
    onSeek: (Float) -> Unit = {},
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val sliderValue = sliderPosition
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f

    val currentStr = formatTime(if (sliderPosition != null) {
        ((sliderPosition ?: 0f) * durationMs).toLong()
    } else {
        positionMs
    })
    val totalStr = if (durationMs > 0) formatTime(durationMs) else "0:00"

    Column(modifier = modifier) {
        val thumbColor = lerp(accentColor, Color.White, 0.35f)
        val activeTrackColor = lerp(accentColor, Color.White, 0.15f)

        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { onSliderPositionChange(it) },
            onValueChangeFinished = {
                sliderPosition?.let(onSeek)
                onSliderPositionChange(null)
            },
            enabled = durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f)
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentStr,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = totalStr,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
internal fun TransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = Dp.Unspecified,
    showBorder: Boolean = true
) {
    val tint = if (isActive) {
        activeColor
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    val borderColor = if (isActive) {
        activeColor.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.25f)
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = 1.dp,
                        color = borderColor,
                        shape = CircleShape
                    )
                } else if (isActive) {
                    Modifier.background(
                        color = activeColor.copy(alpha = 0.18f),
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = if (iconSize == Dp.Unspecified) {
                Modifier.size(30.dp)
            } else {
                Modifier.size(iconSize)
            }
        )
    }
}

@SuppressLint("DefaultLocale")
internal fun formatTime(ms: Long): String {
    return String.format(
        "%d:%02d",
        TimeUnit.MILLISECONDS.toMinutes(ms),
        TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    )
}
