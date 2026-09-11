package com.buga.walkman.ui

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import com.buga.walkman.model.Playlist
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.rememberSongDeleter
import com.buga.walkman.ui.player.CoverPage
import com.buga.walkman.ui.player.MusicPlayerHeader
import com.buga.walkman.ui.player.PlaybackControlsSection
import com.buga.walkman.ui.player.SongInfoSection
import com.buga.walkman.ui.player.SongInfoText
import com.buga.walkman.ui.player.SongProgressBar
import com.buga.walkman.ui.player.TransportButtons
import com.buga.walkman.ui.theme.WalkmanTheme
import com.buga.walkman.viewmodel.PlayerControllerViewModel
import com.buga.walkman.viewmodel.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WalkmanScreen(
    viewModel: PlayerControllerViewModel,
    onOpenQueue: () -> Unit,
    onLogoClick: () -> Unit = {},
    onOpenArtist: (Long) -> Unit = {},
    onOpenAlbum: (Long) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCollapse: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val favoriteIds by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val onDeleteSong = rememberSongDeleter(viewModel)

    BackHandler(onBack = onCollapse)

    WalkmanScreenContent(
        state = state,
        onPlayPause = viewModel::togglePlayPause,
        onNext = viewModel::next,
        onPrev = viewModel::previous,
        onSeek = viewModel::seekToFraction,
        onSelectSong = viewModel::skipToQueueItem,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeat = viewModel::cycleRepeatMode,
        favoriteIds = favoriteIds,
        onToggleFavorite = viewModel::toggleFavorite,
        playlists = playlists,
        onAddToPlaylist = { song, playlistId -> viewModel.addSongToPlaylist(playlistId, song) },
        onOpenQueue = onOpenQueue,
        onLogoClick = onLogoClick,
        onSearchClick = onOpenSearch,
        onMoreClick = {},
        onOpenArtist = onOpenArtist,
        onOpenAlbum = onOpenAlbum,
        onDeleteSong = onDeleteSong,
        onCollapse = onCollapse
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkmanScreenContent(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Float) -> Unit,
    onSelectSong: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Song) -> Unit = {},
    playlists: List<Playlist> = emptyList(),
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    onOpenQueue: () -> Unit,
    onLogoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onOpenArtist: (Long) -> Unit = {},
    onOpenAlbum: (Long) -> Unit = {},
    onDeleteSong: (Song) -> Unit = {},
    onCollapse: () -> Unit = {}
) {
    val colorTransitionSpec = spring<Color>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val animatedGradientTop by animateColorAsState(
        targetValue = state.gradientTop,
        animationSpec = colorTransitionSpec,
        label = "gradientTop"
    )
    val animatedGradientBottom by animateColorAsState(
        targetValue = state.gradientBottom,
        animationSpec = colorTransitionSpec,
        label = "gradientBottom"
    )
    val animatedAccent by animateColorAsState(
        targetValue = state.accentHighlight,
        animationSpec = colorTransitionSpec,
        label = "accentHighlight"
    )
    val animatedTertiary by animateColorAsState(
        targetValue = state.tertiaryColor,
        animationSpec = colorTransitionSpec,
        label = "tertiaryColor"
    )

    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val isScrubbing = sliderPosition != null
    val scrubbingTimeMs = ((sliderPosition ?: 0f) * state.durationMs).toLong()
    val landscape = LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE

    val screenHeightPx = LocalConfiguration.current.screenHeightDp.dp.value *
        LocalConfiguration.current.densityDpi / 160f
    val dismissThreshold = screenHeightPx * 0.2f
    val velocityTracker = remember { VelocityTracker() }
    val dragOffsetY = remember { Animatable(0f) }
    val dismissScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(animatedGradientTop, animatedGradientBottom)
                    )
                )
                .graphicsLayer {
                    val fraction = (dragOffsetY.value / screenHeightPx).coerceIn(0f, 1f)
                    alpha = lerp(1f, 0f, fraction)
                    val scale = lerp(1f, 0.96f, fraction)
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(onCollapse) {
                    var accumulatedDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            accumulatedDrag = 0f
                            velocityTracker.resetTracking()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            accumulatedDrag += dragAmount
                            velocityTracker.addPosition(
                                change.uptimeMillis,
                                change.position
                            )
                            dismissScope.launch {
                                dragOffsetY.snapTo(
                                    (dragOffsetY.value + dragAmount).coerceAtLeast(0f)
                                )
                            }
                        },
                        onDragEnd = {
                            val verticalVelocity = velocityTracker.calculateVelocity().y
                            val velocityThreshold = 150f
                            val minDragThreshold = 5f

                            val shouldCollapse = when {
                                abs(accumulatedDrag) > minDragThreshold ->
                                    accumulatedDrag > 0f
                                abs(verticalVelocity) > velocityThreshold ->
                                    verticalVelocity > 0f
                                else ->
                                    dragOffsetY.value > dismissThreshold
                            }

                            dismissScope.launch {
                                if (shouldCollapse && dragOffsetY.value > 0f) {
                                    withContext(Dispatchers.Main.immediate) {
                                        onCollapse()
                                    }
                                } else {
                                    val fraction = (dragOffsetY.value / screenHeightPx)
                                        .coerceIn(0f, 1f)
                                    val dynamicDamping = lerp(
                                        Spring.DampingRatioNoBouncy,
                                        Spring.DampingRatioLowBouncy,
                                        fraction
                                    )
                                    dragOffsetY.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = dynamicDamping,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            dismissScope.launch {
                                dragOffsetY.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    )
                }
        ) {
        if (landscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                MusicPlayerHeader(
                    onLogoClick = onLogoClick,
                    onQueueClick = onOpenQueue,
                    onSearchClick = onSearchClick,
                    onMoreClick = onMoreClick,
                    currentSong = state.currentSong,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onDeleteSong = onDeleteSong,
                    accentColor = animatedAccent,
                    showBranding = false
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val coverSong = state.currentSong
                        ?: Song(0L, "", "", "", 0L, 0L, 0, 0)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true),
                        contentAlignment = Alignment.Center
                    ) {
                        CoverPage(
                            song = coverSong,
                            isTilted = false,
                            onToggleTilt = {}
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SongInfoText(
                            song = state.currentSong,
                            accentColor = animatedAccent,
                            isScrubbing = isScrubbing,
                            scrubbingTimeMs = scrubbingTimeMs
                        )

                        SongProgressBar(
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            sliderPosition = sliderPosition,
                            onSliderPositionChange = { sliderPosition = it },
                            onSeek = onSeek,
                            accentColor = animatedGradientTop
                        )

                        TransportButtons(
                            isPlaying = state.isPlaying,
                            onPrev = onPrev,
                            onNext = onNext,
                            onPlayPause = onPlayPause,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeat = onCycleRepeat,
                            shuffleEnabled = state.shuffleEnabled,
                            repeatMode = state.repeatMode,
                            accentColor = animatedGradientTop,
                            tertiaryColor = animatedTertiary
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                MusicPlayerHeader(
                    onLogoClick = onLogoClick,
                    onQueueClick = onOpenQueue,
                    onSearchClick = onSearchClick,
                    onMoreClick = onMoreClick,
                    currentSong = state.currentSong,
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onDeleteSong = onDeleteSong,
                    accentColor = animatedAccent
                )

                Spacer(modifier = Modifier.weight(0.15f))

                SongInfoSection(
                    song = state.currentSong,
                    queue = state.queue,
                    currentIndex = state.currentIndex,
                    onSelectSong = onSelectSong,
                    accentColor = animatedAccent,
                    isScrubbing = isScrubbing,
                    scrubbingTimeMs = scrubbingTimeMs,
                    favoriteIds = favoriteIds,
                    onToggleFavorite = onToggleFavorite,
                    playlists = playlists,
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenArtist = onOpenArtist
                )

                Spacer(modifier = Modifier.weight(0.1f))

                PlaybackControlsSection(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    shuffleEnabled = state.shuffleEnabled,
                    repeatMode = state.repeatMode,
                    onSeek = onSeek,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrev = onPrev,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    accentColor = animatedGradientTop,
                    tertiaryColor = animatedTertiary,
                    sliderPosition = sliderPosition,
                    onSliderPositionChange = { sliderPosition = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                )
            }
        }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WalkmanScreenPreview() {
    WalkmanTheme {
        WalkmanScreenContent(
            state = PlayerUiState(
                currentSong = Song(
                    id = 1L,
                    title = "Midnight Drive",
                    artist = "Neon Waves",
                    album = "Nightcall",
                    albumId = 10L,
                    artistId = 20L,
                    duration = 212_000,
                    trackNumber = 3
                ),
                isPlaying = true,
                positionMs = 76_000L,
                durationMs = 212_000L,
                queue = listOf(
                    Song(1L, "Midnight Drive", "Neon Waves", "Nightcall", 10L, 20L, 212_000, 3),
                    Song(2L, "City Lights", "Neon Waves", "Nightcall", 10L, 20L, 195_000, 4)
                ),
                currentIndex = 0,
                shuffleEnabled = false,
                repeatMode = Player.REPEAT_MODE_ALL
            ),
            onPlayPause = {},
            onNext = {},
            onPrev = {},
            onSeek = {},
            onSelectSong = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onOpenQueue = {},
            onOpenArtist = {},
            onOpenAlbum = {}
        )
    }
}
