package com.buga.walkman.ui.screens.detail

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buga.walkman.R
import com.buga.walkman.model.Album
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.AlbumGridCell
import com.buga.walkman.ui.components.ArtistPlaceholder
import com.buga.walkman.ui.components.CoverImage
import com.buga.walkman.ui.components.DeleteSongDialog
import com.buga.walkman.ui.components.expressiveArtistColor
import com.buga.walkman.ui.components.rememberSongDeleter
import com.buga.walkman.viewmodel.LibraryViewModel
import com.buga.walkman.viewmodel.PlayerControllerViewModel
import com.buga.walkman.viewmodel.TrackListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: Long,
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerControllerViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit = {}
) {
    val artists by libraryViewModel.artists.collectAsState()
    val application = LocalContext.current.applicationContext as Application
    val trackListViewModel: TrackListViewModel = viewModel(key = "artist_$artistId") {
        TrackListViewModel(application) { it.loadSongsByArtist(artistId) }
    }
    val tracks by trackListViewModel.tracks.collectAsState()
    val artist = artists.find { it.id == artistId }
    val title = artist?.name ?: tracks.firstOrNull()?.artist.orEmpty()
    val songCount = artist?.songCount?.takeIf { it > 0 } ?: tracks.size
    val albumCount = artist?.albumCount?.takeIf { it > 0 } ?: tracks.map { it.albumId }.distinct().size
    val headerBackground = remember(title) {
        lerp(Color(expressiveArtistColor(title)), Color.Black, 0.4f)
    }
    val playerState by playerViewModel.state.collectAsState()
    val albums = remember(tracks) {
        tracks.groupBy { it.albumId }
            .map { (albumId, albumTracks) ->
                Album(
                    id = albumId,
                    title = albumTracks.first().album,
                    artist = title,
                    songCount = albumTracks.size,
                    year = 0
                )
            }
            .sortedBy { it.title.lowercase() }
    }
    var showAlbums by rememberSaveable { mutableStateOf(false) }
    val tabTracks = stringResource(R.string.tab_songs)
    val tabAlbums = stringResource(R.string.tab_albums)
    var confirmDeleteSong by remember { mutableStateOf<Song?>(null) }
    val requestDelete = rememberSongDeleter(playerViewModel)

    TrackDetailContent(
        title = title,
        subtitle = "",
        metaLine = "",
        coverModel = null,
        tracks = tracks,
        onBack = onBack,
        onPlayAt = { index -> playerViewModel.playSongs(tracks, index) },
        onShuffle = { playerViewModel.playShuffled(tracks) },
        onPlayNextItem = { song -> playerViewModel.playSongsNext(listOf(song)) },
        onAddToQueueItem = { song -> playerViewModel.addSongsToQueue(listOf(song)) },
        onDeleteItem = { song -> confirmDeleteSong = song },
        accent = playerState.accentHighlight,
        tabs = listOf(tabTracks, tabAlbums),
        selectedTab = if (showAlbums) 1 else 0,
        onTabSelected = { showAlbums = (it == 1) },
        albumGrid = {
            albums.chunked(2).forEach { rowAlbums ->
                item(key = "album-${rowAlbums.joinToString("-") { it.id.toString() }}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        rowAlbums.forEach { album ->
                            AlbumGridCell(
                                album = album,
                                onClick = { onOpenAlbum(album.id) },
                                onPlayNext = {
                                    playerViewModel.playSongsNext(tracks.filter { it.albumId == album.id })
                                },
                                onAddToQueue = {
                                    playerViewModel.addSongsToQueue(tracks.filter { it.albumId == album.id })
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                            )
                        }
                        repeat(2 - rowAlbums.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        headerContent = {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    model = null,
                    contentDescription = title,
                    shape = RoundedCornerShape(32.dp),
                    backgroundColor = headerBackground,
                    placeholder = {
                        ArtistPlaceholder(
                            title = title,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(32.dp)
                        )
                    },
                    modifier = Modifier.size(160.dp)
                )

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.format_albums, albumCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = stringResource(R.string.format_songs, songCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )

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