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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buga.walkman.R
import com.buga.walkman.model.Album
import com.buga.walkman.model.Artist
import com.buga.walkman.model.Playlist
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.ArtistPlaceholder
import com.buga.walkman.ui.components.CoverImage
import com.buga.walkman.ui.components.DeleteSongDialog
import com.buga.walkman.ui.components.AddToPlaylistDialog
import com.buga.walkman.ui.components.EmptyState
import com.buga.walkman.ui.components.LocalPlayerCoverAccent
import com.buga.walkman.ui.components.SongListItem
import com.buga.walkman.ui.components.TrackActionsMenu
import com.buga.walkman.ui.components.compositeOver
import com.buga.walkman.ui.components.rememberSongDeleter
import com.buga.walkman.viewmodel.LibraryViewModel
import com.buga.walkman.viewmodel.PlayerControllerViewModel
import com.buga.walkman.viewmodel.PlayerUiState
import kotlinx.coroutines.launch

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
    val favoriteIds by playerViewModel.favorites.collectAsState()
    val playlists by playerViewModel.playlists.collectAsState()
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

    val scope = rememberCoroutineScope()

    val tabAccent = LocalPlayerCoverAccent.current.takeIf { it != Color.Unspecified }
        ?: playerState.accentHighlight

    var confirmDeleteSong by remember { mutableStateOf<Song?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    val requestDelete = rememberSongDeleter(playerViewModel)

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        selectedTabIndex = selectedTab,
                        matchContentSize = true
                    ),
                    color = tabAccent
                )
            },
            divider = {}
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(alpha = 0.7f),
                    text = { Text(title.uppercase(), maxLines = 1) }
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
                    favoriteIds = favoriteIds,
                    onPlaySong = { index -> playerViewModel.playSongs(songs, index) },
                    onPlayNext = { song -> playerViewModel.playSongsNext(listOf(song)) },
                    onAddToQueue = { song -> playerViewModel.addSongsToQueue(listOf(song)) },
                    onAddToPlaylist = { song -> showAddToPlaylistDialog = song },
                    onDelete = { song -> confirmDeleteSong = song },
                    onToggleFavorite = { song -> playerViewModel.toggleFavorite(song) }
                )
                1 -> AlbumsTab(
                    albums = albums,
                    onOpenAlbum = onOpenAlbum,
                    onPlayNext = { album ->
                        scope.launch {
                            playerViewModel.playSongsNext(libraryViewModel.songsByAlbum(album.id))
                        }
                    },
                    onAddToQueue = { album ->
                        scope.launch {
                            playerViewModel.addSongsToQueue(libraryViewModel.songsByAlbum(album.id))
                        }
                    }
                )
                2 -> ArtistsTab(artists = artists, onOpenArtist = onOpenArtist)
                else -> PlaylistsTab(
                    playlists = playlists,
                    favoriteSongs = favoriteSongs,
                    onOpenFavorites = onOpenFavorites,
                    onCreatePlaylist = playerViewModel::createPlaylist
                )
            }
        }
    }

    showAddToPlaylistDialog?.let { song ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = null },
            onAdd = { playlistId ->
                playerViewModel.addSongToPlaylist(playlistId, song)
                showAddToPlaylistDialog = null
            }
        )
    }

    confirmDeleteSong?.let { song ->
        DeleteSongDialog(
            song = song,
            onDismiss = { confirmDeleteSong = null },
            onConfirm = {
                confirmDeleteSong = null
                requestDelete(song)
            }
        )
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    playerState: PlayerUiState,
    favoriteIds: Set<Long>,
    onPlaySong: (Int) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit
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
                onClick = { onPlaySong(index) },
                onPlayNext = { onPlayNext(song) },
                onAddToQueue = { onAddToQueue(song) },
                onAddToPlaylist = { onAddToPlaylist(song) },
                onDelete = { onDelete(song) },
                isFavorite = song.id in favoriteIds,
                onToggleFavorite = { onToggleFavorite(song) }
            )
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<Album>,
    onOpenAlbum: (Long) -> Unit,
    onPlayNext: (Album) -> Unit,
    onAddToQueue: (Album) -> Unit
) {
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
            AlbumGridItem(
                album = album,
                onClick = { onOpenAlbum(album.id) },
                onPlayNext = { onPlayNext(album) },
                onAddToQueue = { onAddToQueue(album) }
            )
        }
    }
}

@Composable
private fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasMenu = onPlayNext != null || onAddToQueue != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            CoverImage(
                model = album.albumArtUri,
                contentDescription = album.title,
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.medium
            )
            if (hasMenu) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_more),
                            tint = Color.White
                        )
                    }
                    TrackActionsMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue
                    )
                }
            }
        }
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
    playlists: List<Playlist>,
    favoriteSongs: List<Song>,
    onOpenFavorites: () -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "new_playlist") {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.new_playlist),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = { showCreateDialog = true })
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            ListItem(
                headlineContent = {
                    Text(
                        text = playlist.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
        if (favoriteSongs.isNotEmpty()) {
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

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    val canCreate = name.isNotBlank()

    val accent = LocalPlayerCoverAccent.current.takeIf { it != Color.Unspecified }
    val containerColor = accent?.let { compositeOver(Color(0x99000000), it) }
    val onColor = containerColor?.let { color ->
        if (color.luminance() > 0.5f) Color.Black else Color.White
    }
    val titleColor = onColor ?: AlertDialogDefaults.titleContentColor
    val textColor = onColor ?: AlertDialogDefaults.textContentColor
    val dialogContainerColor = containerColor ?: AlertDialogDefaults.containerColor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_playlist), color = titleColor) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (canCreate) onCreate(name.trim()) }
                ),
                colors = if (onColor != null && accent != null) {
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = onColor,
                        unfocusedTextColor = onColor,
                        cursorColor = accent,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = onColor.copy(alpha = 0.6f),
                        focusedLabelColor = accent,
                        unfocusedLabelColor = onColor.copy(alpha = 0.7f),
                        focusedSupportingTextColor = onColor.copy(alpha = 0.7f),
                        unfocusedSupportingTextColor = onColor.copy(alpha = 0.7f)
                    )
                } else {
                    OutlinedTextFieldDefaults.colors()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = canCreate,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = accent ?: MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = onColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = dialogContainerColor,
        titleContentColor = titleColor,
        textContentColor = textColor
    )
}
