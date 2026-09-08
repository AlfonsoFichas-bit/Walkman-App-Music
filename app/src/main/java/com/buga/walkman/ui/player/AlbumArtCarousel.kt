package com.buga.walkman.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buga.walkman.R
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.CoverImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumArtCarousel(
    songs: List<Song>,
    currentIndex: Int,
    onSelectSong: (Int) -> Unit,
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Song) -> Unit = {}
) {
    val currentSelectSong by rememberUpdatedState(onSelectSong)
    val currentSongs by rememberUpdatedState(songs)
    val currentIndexState by rememberUpdatedState(currentIndex)
    val carouselState = rememberCarouselState(
        initialItem = currentIndex.coerceAtLeast(0),
        itemCount = { currentSongs.size }
    )

    var isTilted by remember { mutableStateOf(false) }

    LaunchedEffect(carouselState) {
        snapshotFlow { currentIndexState }
            .map { it.coerceAtLeast(0) }
            .distinctUntilChanged()
            .collectLatest { target ->
                if (target in currentSongs.indices) {
                    isTilted = false
                    if (target != carouselState.currentItem) {
                        if (carouselState.isScrollInProgress) {
                            carouselState.scrollToItem(target)
                        } else {
                            carouselState.animateScrollToItem(target)
                        }
                    } else {
                        carouselState.scrollToItem(target)
                    }
                }
            }
    }

    LaunchedEffect(carouselState) {
        snapshotFlow { carouselState.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) {
                    isTilted = false
                }
            }
    }

    // Reconciliador idle: cuando el scroll queda inactivo, el carrusel debe quedar
    // siempre alineado con el índice del ViewModel (única fuente de verdad). Si el
    // usuario deslizó a otra carátula, ese cambio se propaga al player; en cualquier
    // otro caso se fuerza el snap al índice actual, limpiando cualquier offset residual.
    LaunchedEffect(carouselState) {
        snapshotFlow { carouselState.isScrollInProgress }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest { scrolling ->
                if (!scrolling) {
                    delay(150.milliseconds)
                    val settled = carouselState.currentItem
                    val target = currentIndexState.coerceAtLeast(0)
                    if (settled in currentSongs.indices) {
                        if (settled != target) {
                            currentSelectSong(settled)
                        } else {
                            carouselState.scrollToItem(target)
                        }
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            HorizontalUncontainedCarousel(
                state = carouselState,
                itemWidth = maxWidth - 44.dp,
                modifier = Modifier.fillMaxWidth(),
                itemSpacing = 12.dp,
                flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state = carouselState)
            ) { songIndex ->
                val isCurrentItem = songIndex == currentIndexState
                val itemAlpha by animateFloatAsState(
                    targetValue = if (isTilted && !isCurrentItem) 0f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "itemAlpha"
                )

                Box(
                    modifier = Modifier
                        .maskClip(RoundedCornerShape(28.dp))
                        .alpha(itemAlpha)
                ) {
                    songs.getOrNull(songIndex)?.let { song ->
                        CoverPage(
                            song = song,
                            isTilted = isTilted && isCurrentItem,
                            onToggleTilt = { isTilted = !isTilted }
                        )
                    }

                    if (isTilted && isCurrentItem) {
                        val song = songs.getOrNull(songIndex)
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isFavorite = song?.let { it.id in favoriteIds } == true
                            IconButton(
                                onClick = { song?.let(onToggleFavorite) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) {
                                        Icons.Filled.Star
                                    } else {
                                        Icons.Outlined.Star
                                    },
                                    contentDescription = stringResource(R.string.cd_favorite),
                                    tint = if (isFavorite) Color(0xFFFFD700) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoverPage(song: Song, isTilted: Boolean, onToggleTilt: () -> Unit) {
    val tiltAngle by animateFloatAsState(
        targetValue = if (isTilted) 20f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tiltAngle"
    )
    val hasAlbumArt = song.albumId > 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onToggleTilt() }
    ) {
        if (hasAlbumArt) {
            CoverImage(
                model = song.albumArtUri,
                contentDescription = stringResource(R.string.cd_album_art),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = tiltAngle
                        cameraDistance = 12f * density
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                shape = RoundedCornerShape(28.dp)
            )
        } else {
            NoAlbumArtCover(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = tiltAngle
                        cameraDistance = 12f * density
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
            )
        }
    }
}

@Composable
internal fun NoAlbumArtCover(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3D2260),
                        Color(0xFF1A0F2E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.light_disc),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(180.dp)
        )
    }
}

@Composable
internal fun EmptyCover() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u26AB",
            fontSize = 96.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
    }
}
