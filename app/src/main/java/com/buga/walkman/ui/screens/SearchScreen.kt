package com.buga.walkman.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buga.walkman.R
import com.buga.walkman.model.Album
import com.buga.walkman.model.SearchLogic
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.AlbumGridCell
import com.buga.walkman.ui.components.EmptyState
import com.buga.walkman.ui.components.SongListItem
import com.buga.walkman.viewmodel.LibraryViewModel
import com.buga.walkman.viewmodel.PlayerControllerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerControllerViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    val songs by libraryViewModel.songs.collectAsState()
    val albums by libraryViewModel.albums.collectAsState()
    val playerState by playerViewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showAllSongs by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(query) {
        showAllSongs = false
    }

    val filteredSongs = SearchLogic.filterSongs(songs, query)
    val filteredAlbums = SearchLogic.filterAlbums(albums, query)
    val visibleSongs = if (showAllSongs) filteredSongs else filteredSongs.take(5)
    val hasQuery = query.isNotBlank()
    val hasResults = filteredSongs.isNotEmpty() || filteredAlbums.isNotEmpty()

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
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = playerState.accentHighlight,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = playerState.accentHighlight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when {
                    !hasQuery -> item(key = "empty") {
                        EmptyState(stringResource(R.string.search_hint))
                    }
                    !hasResults -> item(key = "empty") {
                        EmptyState(stringResource(R.string.search_no_results))
                    }
                    else -> {
                        if (filteredSongs.isNotEmpty()) {
                            item(key = "songs_header") {
                                SectionHeader(stringResource(R.string.tab_songs))
                            }
                            itemsIndexed(visibleSongs, key = { _, song -> song.id }) { index, song ->
                                SongListItem(
                                    song = song,
                                    isCurrentAndPlaying = playerState.currentSong?.id == song.id &&
                                        playerState.isPlaying,
                                    onClick = { playerViewModel.playSongs(visibleSongs, index) }
                                )
                            }
                            if (filteredSongs.size > 5) {
                                item(key = "show_more_songs") {
                                    ShowMoreButton(
                                        text = if (showAllSongs) {
                                            stringResource(R.string.search_show_less)
                                        } else {
                                            stringResource(R.string.search_show_more_songs)
                                        },
                                        accent = playerState.accentHighlight,
                                        onClick = { showAllSongs = !showAllSongs }
                                    )
                                }
                            }
                        }

                        if (filteredAlbums.isNotEmpty()) {
                            item(key = "divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            item(key = "albums_header") {
                                SectionHeader(stringResource(R.string.tab_albums))
                            }
                            albumGrid(filteredAlbums, onOpenAlbum)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ShowMoreButton(text: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
    }
}

private fun LazyListScope.albumGrid(
    albums: List<Album>,
    onOpenAlbum: (Long) -> Unit
) {
    albums.chunked(2).forEach { rowAlbums ->
        item(key = "album-row-${rowAlbums.joinToString("-") { it.id.toString() }}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                rowAlbums.forEach { album ->
                    AlbumGridCell(
                        album = album,
                        onClick = { onOpenAlbum(album.id) },
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
}
