package com.buga.walkman.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buga.walkman.R
import com.buga.walkman.model.Album
import com.buga.walkman.model.Artist
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.ArtistPlaceholder
import com.buga.walkman.ui.components.CoverImage
import com.buga.walkman.ui.components.EmptyState
import com.buga.walkman.ui.components.SongListItem
import com.buga.walkman.viewmodel.LibraryViewModel
import com.buga.walkman.viewmodel.PlayerControllerViewModel
import com.buga.walkman.viewmodel.PlayerUiState

private val WalkmanFavoriteGold = Color(0xFFFFD700)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerControllerViewModel = viewModel(),
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenFavorites: () -> Unit
) {
    val songs by libraryViewModel.songs.collectAsState()
    val albums by libraryViewModel.albums.collectAsState()
    val artists by libraryViewModel.artists.collectAsState()
    val favoriteSongs by playerViewModel.favoriteSongs.collectAsState()
    val playerState by playerViewModel.state.collectAsState()

    val tabTitles = listOf(
        stringResource(R.string.tab_songs),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_playlists)
    )

    LaunchedEffect(Unit) {
        libraryViewModel.loadAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            divider = {}
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(alpha = 0.7f),
                    text = { Text(title, maxLines = 1) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> SongsTab(
                    songs = songs,
                    playerState = playerState,
                    onPlaySong = { index -> playerViewModel.playSongs(songs, index) }
                )
                1 -> AlbumsTab(albums = albums, onOpenAlbum = onOpenAlbum)
                2 -> ArtistsTab(artists = artists, onOpenArtist = onOpenArtist)
                else -> PlaylistsTab(
                    favoriteSongs = favoriteSongs,
                    onOpenFavorites = onOpenFavorites
                )
            }
        }
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    playerState: PlayerUiState,
    onPlaySong: (Int) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(stringResource(R.string.no_songs_found))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongListItem(
                song = song,
                isCurrentAndPlaying = songs.getOrNull(playerState.currentIndex)?.id == song.id &&
                    playerState.isPlaying,
                onClick = { onPlaySong(index) }
            )
        }
    }
}

@Composable
private fun AlbumsTab(albums: List<Album>, onOpenAlbum: (Long) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState(stringResource(R.string.no_songs_found))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumGridItem(album = album, onClick = { onOpenAlbum(album.id) })
        }
    }
}

@Composable
private fun AlbumGridItem(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        CoverImage(
            model = album.albumArtUri,
            contentDescription = album.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.medium
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyLarge,
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
private fun ArtistsTab(artists: List<Artist>, onOpenArtist: (Long) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState(stringResource(R.string.no_songs_found))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.id }) { artist ->
            ListItem(
                headlineContent = {
                    Text(
                        artist.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.format_songs, artist.songCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    ArtistPlaceholder(
                        title = artist.name,
                        modifier = Modifier
                            .height(48.dp)
                            .aspectRatio(1f)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = { onOpenArtist(artist.id) })
            )
        }
    }
}

@Composable
private fun PlaylistsTab(
    favoriteSongs: List<Song>,
    onOpenFavorites: () -> Unit
) {
    if (favoriteSongs.isEmpty()) {
        EmptyState(stringResource(R.string.no_playlists))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "favorites") {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.home_favorites),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.format_songs, favoriteSongs.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(WalkmanFavoriteGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = WalkmanFavoriteGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onOpenFavorites)
            )
        }
    }
}
