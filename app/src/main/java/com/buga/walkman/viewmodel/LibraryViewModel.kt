package com.buga.walkman.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buga.walkman.data.LibraryEvents
import com.buga.walkman.data.MediaRepository
import com.buga.walkman.data.PlayerPersistence
import com.buga.walkman.data.db.AppDatabase
import com.buga.walkman.data.db.FolderState
import com.buga.walkman.model.Album
import com.buga.walkman.model.Artist
import com.buga.walkman.model.Playlist
import com.buga.walkman.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(getApplication())

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            FolderState.folder.collect { reload() }
        }
        viewModelScope.launch {
            LibraryEvents.reload.collect {
                reload()
            }
        }
    }

    fun loadAll() {
        reload()
    }

    suspend fun songsByAlbum(albumId: Long): List<Song> =
        repository.loadSongsByAlbum(albumId)

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val loadedSongs = repository.loadSongs()
            _songs.value = loadedSongs
            _albums.value = repository.loadAlbumsForSongs(loadedSongs)
            _artists.value = repository.loadArtistsForSongs(loadedSongs)
            _isLoading.value = false
        }
    }
}

open class TrackListViewModel(
    application: Application,
    private val loader: suspend (MediaRepository) -> List<Song>
) : AndroidViewModel(application) {

    private val _tracks = MutableStateFlow<List<Song>>(emptyList())
    val tracks: StateFlow<List<Song>> = _tracks.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _tracks.value = loader(MediaRepository(getApplication()))
        }
        viewModelScope.launch {
            LibraryEvents.reload.collect {
                _tracks.value = loader(MediaRepository(getApplication()))
            }
        }
    }
}

class PlaylistDetailViewModel(
    application: Application,
    private val playlistId: Long
) : AndroidViewModel(application) {

    private val persistence =
        PlayerPersistence(getApplication(), AppDatabase.getInstance(getApplication()))

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        viewModelScope.launch {
            persistence.observePlaylist(playlistId).collect { _playlist.value = it }
        }
        viewModelScope.launch {
            persistence.observeSongsInPlaylist(playlistId).collect { _songs.value = it }
        }
    }

    fun removeSong(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            persistence.removeSongFromPlaylist(playlistId, songId)
        }
    }
}