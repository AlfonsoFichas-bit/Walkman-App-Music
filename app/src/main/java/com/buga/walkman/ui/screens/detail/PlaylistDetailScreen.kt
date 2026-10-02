package com.buga.walkman.ui.screens.detail

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buga.walkman.R
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.AddToPlaylistDialog
import com.buga.walkman.ui.components.DeleteSongDialog
import com.buga.walkman.ui.components.rememberSongDeleter
import com.buga.walkman.viewmodel.PlaylistDetailViewModel
import com.buga.walkman.viewmodel.PlayerControllerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playerViewModel: PlayerControllerViewModel,
    onBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val playlistViewModel: PlaylistDetailViewModel = viewModel(key = "playlist_$playlistId") {
        PlaylistDetailViewModel(application, playlistId)
    }

    val playlist by playlistViewModel.playlist.collectAsState()
    val songs by playlistViewModel.songs.collectAsState()
    val playlists by playerViewModel.playlists.collectAsState()
    val playerState by playerViewModel.state.collectAsState()
    val favoriteIds by playerViewModel.favorites.collectAsState()

    val title = playlist?.name.orEmpty()
    val accent = playerState.accentHighlight

    var confirmDeleteSong by remember { mutableStateOf<Song?>(null) }
    var addToPlaylistSong by remember { mutableStateOf<Song?>(null) }
    val requestDelete = rememberSongDeleter(playerViewModel)

    TrackDetailContent(
        title = "",
        subtitle = "",
        metaLine = "",
        coverModel = null,
        tracks = songs,
        onBack = onBack,
        onPlayAt = { index -> playerViewModel.playSongs(songs, index) },
        onShuffle = { playerViewModel.playShuffled(songs) },
        accent = accent,
        onPlayNextItem = { song -> playerViewModel.playSongsNext(listOf(song)) },
        onAddToQueueItem = { song -> playerViewModel.addSongsToQueue(listOf(song)) },
        onAddToPlaylistItem = { song -> addToPlaylistSong = song },
        onRemoveFromPlaylistItem = { song -> playlistViewModel.removeSong(song.id) },
        onDeleteItem = { song -> confirmDeleteSong = song },
        favoriteIds = favoriteIds,
        onToggleFavoriteItem = { song -> playerViewModel.toggleFavorite(song) },
        headerContent = {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(72.dp)
                    )
                }

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
                        text = stringResource(R.string.format_songs, songs.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (songs.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.playlist_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )

    addToPlaylistSong?.let { song ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylistSong = null },
            onAdd = { targetPlaylistId ->
                playerViewModel.addSongToPlaylist(targetPlaylistId, song)
                addToPlaylistSong = null
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