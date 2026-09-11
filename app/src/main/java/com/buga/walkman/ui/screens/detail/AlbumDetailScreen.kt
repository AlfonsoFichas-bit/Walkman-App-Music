package com.buga.walkman.ui.screens.detail

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buga.walkman.R
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.CoverImage
import com.buga.walkman.ui.components.DeleteSongDialog
import com.buga.walkman.ui.components.rememberSongDeleter
import com.buga.walkman.viewmodel.LibraryViewModel
import com.buga.walkman.viewmodel.PlayerControllerViewModel
import com.buga.walkman.viewmodel.TrackListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerControllerViewModel = viewModel(),
    onBack: () -> Unit
) {
    val albums by libraryViewModel.albums.collectAsState()
    val application = LocalContext.current.applicationContext as Application
    val trackListViewModel: TrackListViewModel = viewModel(key = "album_$albumId") {
        TrackListViewModel(application) { it.loadSongsByAlbum(albumId) }
    }
    val tracks by trackListViewModel.tracks.collectAsState()
    val album = albums.find { it.id == albumId }
    val playerState by playerViewModel.state.collectAsState()
    val title = album?.title ?: tracks.firstOrNull()?.album.orEmpty()
    val artist = album?.artist ?: tracks.firstOrNull()?.artist.orEmpty()
    val songCount = album?.songCount ?: tracks.size

    var confirmDeleteSong by remember { mutableStateOf<Song?>(null) }
    val requestDelete = rememberSongDeleter(playerViewModel)

    TrackDetailContent(
        title = "",
        subtitle = "",
        metaLine = "",
        coverModel = album?.albumArtUri ?: tracks.firstOrNull()?.albumArtUri,
        tracks = tracks,
        onBack = onBack,
        onPlayAt = { index -> playerViewModel.playSongs(tracks, index) },
        onShuffle = { playerViewModel.playShuffled(tracks) },
        onPlayNextItem = { song -> playerViewModel.playSongsNext(listOf(song)) },
        onAddToQueueItem = { song -> playerViewModel.addSongsToQueue(listOf(song)) },
        onDeleteItem = { song -> confirmDeleteSong = song },
        accent = playerState.accentHighlight,
        headerContent = {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    model = album?.albumArtUri ?: tracks.firstOrNull()?.albumArtUri,
                    contentDescription = title,
                    shape = RoundedCornerShape(32.dp),
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
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

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