package com.buga.walkman.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buga.walkman.R
import com.buga.walkman.ui.components.EmptyState
import com.buga.walkman.ui.components.LocalPlayerCoverAccent
import com.buga.walkman.ui.components.PlaybackFabMenu
import com.buga.walkman.ui.components.SongListItem
import com.buga.walkman.viewmodel.PlayerControllerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    playerViewModel: PlayerControllerViewModel = viewModel(),
    onBack: () -> Unit
) {
    val favoriteSongs by playerViewModel.favoriteSongs.collectAsState()
    val playerState by playerViewModel.state.collectAsState()
    val accent = LocalPlayerCoverAccent.current.takeIf { it != Color.Unspecified }
        ?: playerState.accentHighlight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.home_favorites),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            if (favoriteSongs.isEmpty()) {
                EmptyState(stringResource(R.string.no_favorites))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(favoriteSongs, key = { _, song -> song.id }) { index, song ->
                        SongListItem(
                            song = song,
                            isCurrentAndPlaying = playerState.currentSong?.id == song.id &&
                                playerState.isPlaying,
                            onClick = { playerViewModel.playSongs(favoriteSongs, index) },
                            onPlayNext = { playerViewModel.playSongsNext(listOf(song)) },
                            onAddToQueue = { playerViewModel.addSongsToQueue(listOf(song)) },
                            isFavorite = true,
                            onToggleFavorite = { playerViewModel.toggleFavorite(song) }
                        )
                    }
                }
            }
        }

        if (favoriteSongs.isNotEmpty()) {
            PlaybackFabMenu(
                accent = accent,
                onPlayAll = { playerViewModel.playSongs(favoriteSongs, 0) },
                onShuffle = { playerViewModel.playShuffled(favoriteSongs) },
                enabled = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }
}