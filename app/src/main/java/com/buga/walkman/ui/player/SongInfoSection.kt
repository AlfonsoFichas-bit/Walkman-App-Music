package com.buga.walkman.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buga.walkman.R
import com.buga.walkman.model.Playlist
import com.buga.walkman.model.Song

@Composable
internal fun SongInfoSection(
    song: Song?,
    queue: List<Song>,
    currentIndex: Int,
    onSelectSong: (Int) -> Unit,
    accentColor: Color,
    isScrubbing: Boolean = false,
    scrubbingTimeMs: Long = 0L,
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Song) -> Unit = {},
    playlists: List<Playlist> = emptyList(),
    onAddToPlaylist: (Song, Long) -> Unit = { _, _ -> },
    onOpenArtist: (Long) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        SongInfoText(
            song = song,
            accentColor = accentColor,
            isScrubbing = isScrubbing,
            scrubbingTimeMs = scrubbingTimeMs
        )

        Spacer(modifier = Modifier.height(30.dp))

        if (queue.isEmpty()) {
            EmptyCover()
        } else {
            AlbumArtCarousel(
                songs = queue,
                currentIndex = currentIndex,
                onSelectSong = onSelectSong,
                favoriteIds = favoriteIds,
                onToggleFavorite = onToggleFavorite,
                playlists = playlists,
                onAddToPlaylist = onAddToPlaylist,
                onOpenArtist = onOpenArtist
            )
        }
    }
}

@Composable
internal fun SongInfoText(
    song: Song?,
    accentColor: Color,
    isScrubbing: Boolean = false,
    scrubbingTimeMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(intrinsicSize = IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(color = accentColor)
            )

            Spacer(modifier = Modifier.width(13.dp))

            Column {
                Text(
                    text = song?.title ?: stringResource(R.string.nothing_playing),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.artist ?: stringResource(R.string.unknown_artist),
                    fontSize = 20.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = song?.album ?: "",
                    fontSize = 14.sp,
                    color = Color(0xA6FEFEFE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isScrubbing,
            enter = expandVertically(
                spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(
                spring(stiffness = Spring.StiffnessMediumLow)
            ),
            exit = shrinkVertically(
                spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(
                spring(stiffness = Spring.StiffnessMediumLow)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier
                ) {
                    Text(
                        text = formatTime(scrubbingTimeMs),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
