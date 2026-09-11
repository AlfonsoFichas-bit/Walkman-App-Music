package com.buga.walkman.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buga.walkman.R
import com.buga.walkman.viewmodel.PlayerUiState
import kotlinx.coroutines.launch

@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    darkOverlay: Color = Color(0xCC000000)
) {
    val song = state.currentSong ?: return
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f

    val accent by animateColorAsState(state.accentHighlight, label = "miniPlayerAccent")
    val containerColor = compositeOver(darkOverlay, accent)
    val onContainer = if (containerColor.luminance() > 0.5f) Color.Black else Color.White
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            contentColor = onContainer,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .graphicsLayer {
                    translationX = offsetX.value
                    rotationZ = offsetX.value / screenWidthPx * 8f
                }
                .pointerInput(onNext, onPrevious, screenWidthPx) {
                    val threshold = with(density) { 140.dp.toPx() }
                    val springSpec = spring<Float>(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    detectHorizontalDragGestures(
                        onDragStart = {
                            scope.launch { offsetX.stop() }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        },
                        onDragEnd = {
                            scope.launch {
                                val current = offsetX.value
                                when {
                                    current > threshold -> onPrevious()
                                    current < -threshold -> onNext()
                                }
                                offsetX.animateTo(targetValue = 0f, animationSpec = springSpec)
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(targetValue = 0f, animationSpec = springSpec)
                            }
                        }
                    )
                }
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    CoverImage(
                        model = song.albumArtUri,
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.size(48.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.labelMedium,
                            color = onContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                            tint = accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
