package com.buga.walkman.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buga.walkman.R
import com.buga.walkman.model.Album
import com.buga.walkman.model.Artist
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.ArtistPlaceholder
import com.buga.walkman.ui.components.CoverImage
import com.buga.walkman.ui.components.InitialsPlaceholder
import com.buga.walkman.viewmodel.LibraryViewModel
import com.buga.walkman.viewmodel.PlayerControllerViewModel

@Composable
fun HomeScreen(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerControllerViewModel = viewModel(),
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit
) {
    val songs by libraryViewModel.songs.collectAsState()
    val albums by libraryViewModel.albums.collectAsState()
    val artists by libraryViewModel.artists.collectAsState()
    val favoriteSongs by playerViewModel.favoriteSongs.collectAsState()
    val recentSongs by playerViewModel.recentSongs.collectAsState()
    val topSongs by playerViewModel.topSongs.collectAsState()
    val playerState by playerViewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp)
    ) {
        if (playerState.queue.isNotEmpty()) {
            item(key = "queue") {
                SectionTitle(stringResource(R.string.home_queue))
                QueueCarousel(
                    songs = playerState.queue,
                    currentIndex = playerState.currentIndex,
                    accent = playerState.accentHighlight,
                    onPlay = { index -> playerViewModel.playSongs(playerState.queue, index) }
                )
            }
        } else {
            item(key = "listen_next") {
                SectionTitle(stringResource(R.string.home_listen_next))
                ListenNextRow(
                    songs = songs,
                    onPlay = { index -> playerViewModel.playSongs(songs, index) }
                )
            }
        }

        if (favoriteSongs.isNotEmpty()) {
            item(key = "favorites") {
                SectionTitle(stringResource(R.string.home_favorites))
                ListenNextRow(
                    songs = favoriteSongs,
                    onPlay = { index -> playerViewModel.playSongs(favoriteSongs, index) }
                )
            }
        }

        if (recentSongs.isNotEmpty()) {
            item(key = "recent") {
                SectionTitle(stringResource(R.string.home_recent))
                ListenNextRow(
                    songs = recentSongs,
                    onPlay = { index -> playerViewModel.playSongs(recentSongs, index) }
                )
            }
        }

        item(key = "new_releases") {
            SectionTitle(stringResource(R.string.home_new_releases))
            val recentAlbums = albums.sortedByDescending { it.year }.take(10)
            if (recentAlbums.isEmpty()) {
                EmptySection(stringResource(R.string.home_no_albums))
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentAlbums, key = { it.id }) { album ->
                        AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                    }
                }
            }
        }

        item(key = "top_songs") {
            SectionTitle(stringResource(R.string.home_top_songs))
            val displayTopSongs = topSongs.ifEmpty { songs.take(5) }
            TopSongsColumn(
                topSongs = displayTopSongs,
                onPlay = { index -> playerViewModel.playSongs(displayTopSongs, index) }
            )
        }

        item(key = "featured_artists") {
            SectionTitle(stringResource(R.string.home_featured_artists))
            if (artists.isEmpty()) {
                EmptySection(stringResource(R.string.home_no_artists))
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artists.take(12), key = { it.id }) { artist ->
                        ArtistCircle(artist = artist, onClick = { onOpenArtist(artist.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun ListenNextRow(
    songs: List<Song>,
    onPlay: (Int) -> Unit
) {
    if (songs.isEmpty()) {
        EmptySection(stringResource(R.string.no_songs_found))
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs.take(10), key = { it.id }) { song ->
            ListenNextCard(song = song, onClick = { onPlay(songs.indexOf(song).coerceAtLeast(0)) })
        }
    }
}

@Composable
private fun ListenNextCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        CoverImage(
            model = song.albumArtUri,
            contentDescription = song.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.large,
            placeholder = { InitialsPlaceholder(song.title, song.artist, Modifier.fillMaxSize()) }
        )
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QueueCarousel(
    songs: List<Song>,
    currentIndex: Int,
    accent: Color,
    onPlay: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex in songs.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            QueueCard(
                song = song,
                accent = accent,
                isCurrent = index == currentIndex,
                onClick = { onPlay(index) }
            )
        }
    }
}

@Composable
private fun QueueCard(
    song: Song,
    accent: Color,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .border(
                    border = BorderStroke(
                        width = if (isCurrent) 3.dp else 0.dp,
                        color = if (isCurrent) accent else Color.Transparent
                    ),
                    shape = MaterialTheme.shapes.large
                )
        ) {
            CoverImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.large,
                placeholder = { InitialsPlaceholder(song.title, song.artist, Modifier.fillMaxSize()) }
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(width = 20.dp, height = 20.dp)
                    )
                }
            }
        }
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        CoverImage(
            model = album.albumArtUri,
            contentDescription = album.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.large,
            placeholder = { InitialsPlaceholder(album.title, album.artist, Modifier.fillMaxSize()) }
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopSongsColumn(
    topSongs: List<Song>,
    onPlay: (Int) -> Unit
) {
    if (topSongs.isEmpty()) {
        EmptySection(stringResource(R.string.no_songs_found))
        return
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        topSongs.forEachIndexed { index, song ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(index) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp)
                    ) {
                        CoverImage(
                            model = song.albumArtUri,
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxSize(),
                            placeholder = { InitialsPlaceholder(song.title, song.artist, Modifier.fillMaxSize()) }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.formatDuration(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistCircle(artist: Artist, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clickable(onClick = onClick)
    ) {
        ArtistPlaceholder(
            title = artist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun EmptySection(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
