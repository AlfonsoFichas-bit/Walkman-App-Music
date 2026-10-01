@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.buga.walkman.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.buga.walkman.data.AudioTagWriter
import com.buga.walkman.data.CoverStore
import com.buga.walkman.data.LibraryEvents
import com.buga.walkman.data.PlayerPersistence
import com.buga.walkman.data.db.AppDatabase
import com.buga.walkman.data.media.ArtworkColorExtractor
import com.buga.walkman.model.EXTRA_DURATION_MS
import com.buga.walkman.model.Playlist
import com.buga.walkman.model.Song
import com.buga.walkman.model.toMediaItem
import com.buga.walkman.model.toSong
import com.buga.walkman.service.PlaybackService
import com.buga.walkman.ui.theme.WalkmanAccentHighlight
import com.buga.walkman.ui.theme.WalkmanGradientBottom
import com.buga.walkman.ui.theme.WalkmanGradientTop
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val cassetteView: Boolean = false,
    val gradientTop: Color = WalkmanGradientTop,
    val gradientBottom: Color = WalkmanGradientBottom,
    val accentHighlight: Color = WalkmanAccentHighlight,
    val tertiaryColor: Color = WalkmanAccentHighlight
)

class PlayerControllerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val database = AppDatabase.getInstance(getApplication())
    private val persistence = PlayerPersistence(getApplication(), database)
    private val artworkColors = ArtworkColorExtractor(getApplication())

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    private val _recentSongs = MutableStateFlow<List<Song>>(emptyList())
    val recentSongs: StateFlow<List<Song>> = _recentSongs.asStateFlow()

    private val _topSongs = MutableStateFlow<List<Song>>(emptyList())
    val topSongs: StateFlow<List<Song>> = _topSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _controller = MutableStateFlow<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var restorePending = false
    private var originalQueue: List<Song> = emptyList()
    private var shuffleActive = false
    private var paletteJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshCurrent()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshCurrent()
            mediaItem?.toSong()?.let { recordPlay(it) }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            refreshQueue()
            refreshCurrent()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val song = _controller.value?.currentMediaItem?.toSong()
            Log.e(
                TAG,
                "Playback error [${error.errorCodeName}] for '${song?.title}' " +
                    "uri=${song?.uri}: ${error.message}",
                error
            )
        }
    }

    init {
        connect()
        observePersistence()
        restorePlayerView()
        viewModelScope.launch {
            while (isActive) {
                _controller.value?.let { controller ->
                    if (controller.playbackState != Player.STATE_IDLE) {
                        val position = controller.currentPosition.coerceAtLeast(0)
                        val duration = controller.duration
                            .takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                        _state.update { it.copy(positionMs = position, durationMs = duration) }
                        persistence.savePosition(controller.currentMediaItemIndex, position)
                    }
                }
                delay(500)
            }
        }
    }

    private fun observePersistence() {
        viewModelScope.launch {
            persistence.observeFavorites().collect { list ->
                _favorites.value = list.map { it.id }.toSet()
                _favoriteSongs.value = list
            }
        }
        viewModelScope.launch {
            persistence.observeRecent().collect { list ->
                _recentSongs.value = list
            }
        }
        viewModelScope.launch {
            persistence.observeTopPlayed().collect { list ->
                _topSongs.value = list
            }
        }
        viewModelScope.launch {
            persistence.observePlaylists().collect { list ->
                _playlists.value = list
            }
        }
    }

    private fun connect() {
        val context = getApplication<Application>()
        val executor = ContextCompat.getMainExecutor(context)
        try {
            val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    val controller = future.get()
                    controller.addListener(listener)
                    _controller.value = controller
                    restorePending = true
                    restoreShuffle(controller)
                    restoreRepeatMode(controller)
                    refreshQueue(controller)
                    refreshCurrent(controller)
                    restoreQueueIfNeeded(controller)
                } catch (_: Exception) {
                }
            }, executor)
        } catch (_: Exception) {
        }
    }

    private fun refreshCurrent(player: Player? = _controller.value) {
        player ?: return
        val mediaItem = player.currentMediaItem
        val song = mediaItem?.toSong()
        val metadataDuration =
            mediaItem?.mediaMetadata?.extras?.getInt(EXTRA_DURATION_MS)?.toLong() ?: 0L
        val playerDuration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        _state.update {
            it.copy(
                currentSong = song,
                durationMs = if (playerDuration > 0) playerDuration else metadataDuration,
                currentIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex else -1
            )
        }
        if (song != null) {
            // Cancelled on every new track so skipping quickly cannot leave several Palette
            // computations racing to write the gradient, where the last to finish would win instead
            // of the last one requested.
            paletteJob?.cancel()
            paletteJob = viewModelScope.launch {
                val colors = artworkColors.colorsFor(song.albumId, song.albumArtUri)
                _state.update {
                    it.copy(
                        gradientTop = colors.top,
                        gradientBottom = colors.bottom,
                        accentHighlight = colors.highlight,
                        tertiaryColor = colors.tertiary
                    )
                }
            }
        }
    }

    private fun refreshQueue(player: Player? = _controller.value) {
        player ?: return
        val items = buildList {
            for (i in 0 until player.mediaItemCount) {
                add(player.getMediaItemAt(i).toSong())
            }
        }
        _state.update {
            it.copy(queue = items)
        }
        refreshCurrent(player)
        val toSave = originalQueue
        viewModelScope.launch {
            if (items.isEmpty() && restorePending) return@launch
            persistence.saveQueue(toSave)
        }
    }

    private fun restoreShuffle(controller: MediaController) {
        shuffleActive = persistence.loadShuffleEnabled()
        _state.update { it.copy(shuffleEnabled = shuffleActive) }
    }

    private fun restorePlayerView() {
        _state.update { it.copy(cassetteView = persistence.loadPlayerViewMode()) }
    }

    fun togglePlayerView() {
        val newMode = !_state.value.cassetteView
        _state.update { it.copy(cassetteView = newMode) }
        persistence.savePlayerViewMode(newMode)
    }

    private fun restoreRepeatMode(controller: MediaController) {
        val saved = persistence.loadRepeatMode()
        controller.repeatMode = saved
        _state.update { it.copy(repeatMode = saved) }
    }

    private fun restoreQueueIfNeeded(controller: MediaController) {
        if (controller.mediaItemCount > 0) {
            restorePending = false
            return
        }
        viewModelScope.launch {
            val items = persistence.loadQueue()
            restorePending = false
            if (items.isNotEmpty() && controller.mediaItemCount == 0) {
                originalQueue = items.toList()
                val saved = persistence.loadPosition()
                val display: List<Song>
                val startIndex: Int
                val startPosition: Long
                if (shuffleActive) {
                    val anchor = items.getOrNull(saved.index.coerceIn(0, items.lastIndex))
                        ?: items.firstOrNull()
                    display = anchor?.let { anchoredShuffle(items, it) } ?: items.shuffled()
                    startIndex = 0
                    startPosition = 0L
                } else {
                    display = items
                    startIndex = saved.index.coerceIn(0, items.lastIndex)
                    startPosition = saved.positionMs
                }
                controller.setMediaItems(display.map { it.toMediaItem() }, startIndex, startPosition)
                controller.prepare()
            }
        }
    }

    private fun recordPlay(song: Song) {
        viewModelScope.launch {
            persistence.recordPlay(song)
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        val controller = _controller.value ?: return
        originalQueue = songs.toList()
        val display: List<Song>
        val start: Int
        if (shuffle) {
            val anchor = songs.getOrNull(startIndex)
            display = anchor?.let { anchoredShuffle(songs, it) } ?: songs.shuffled()
            start = 0
        } else {
            display = songs
            start = startIndex
        }
        rebuildQueue(controller, display, start, shuffle)
        controller.prepare()
        controller.play()
    }

    fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        val controller = _controller.value ?: return
        originalQueue = songs.toList()
        val anchor = songs.random()
        rebuildQueue(controller, anchoredShuffle(songs, anchor), 0, true)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = _controller.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() {
        _controller.value?.seekToNext()
    }

    fun previous() {
        _controller.value?.seekToPrevious()
    }

    fun seekToFraction(fraction: Float) {
        val controller = _controller.value ?: return
        val duration = _state.value.durationMs
        if (duration <= 0) return
        controller.seekTo((fraction * duration).toLong().coerceIn(0, duration))
    }

    fun toggleShuffle() {
        val controller = _controller.value ?: return
        if (shuffleActive) {
            disableShuffle(controller)
        } else {
            enableShuffle(controller)
        }
    }

    private fun enableShuffle(controller: Player) {
        val anchor = currentSong(controller)
        val display = anchor?.let { anchoredShuffle(originalQueue, it) }
            ?: if (originalQueue.isEmpty()) emptyList() else originalQueue.shuffled()
        rebuildQueue(controller, display, 0, true, controller.currentPosition)
    }

    private fun disableShuffle(controller: Player) {
        val anchor = currentSong(controller)
        val index = originalQueue.indexOfFirst { it.id == anchor?.id }
        rebuildQueue(controller, originalQueue, index.coerceAtLeast(0), false, controller.currentPosition)
    }

    private fun rebuildQueue(
        controller: Player,
        display: List<Song>,
        startIndex: Int,
        shuffle: Boolean,
        startPositionMs: Long = 0L
    ) {
        controller.shuffleModeEnabled = false
        controller.setMediaItems(display.map { it.toMediaItem() }, startIndex, startPositionMs)
        shuffleActive = shuffle
        _state.update {
            it.copy(
                queue = display,
                shuffleEnabled = shuffle,
                currentIndex = startIndex
            )
        }
        viewModelScope.launch { persistence.saveShuffleEnabled(shuffle) }
        persistence.savePosition(startIndex, startPositionMs)
    }

    private fun anchoredShuffle(songs: List<Song>, anchor: Song): List<Song> {
        if (songs.isEmpty()) return songs
        val index = songs.indexOfFirst { it.id == anchor.id }
        if (index < 0) return songs.shuffled()
        return listOf(anchor) + songs.filterIndexed { i, _ -> i != index }.shuffled()
    }

    private fun currentSong(controller: Player): Song? = controller.currentMediaItem?.toSong()

    fun cycleRepeatMode() {
        val controller = _controller.value ?: return
        val newMode = when (_state.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = newMode
        viewModelScope.launch { persistence.saveRepeatMode(newMode) }
    }

    fun skipToQueueItem(index: Int) {
        _controller.value?.seekToDefaultPosition(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _controller.value?.moveMediaItem(fromIndex, toIndex)
    }

    fun removeFromQueue(index: Int) {
        _controller.value?.removeMediaItem(index)
    }

    fun addSongsToQueue(songs: List<Song>) {
        val controller = _controller.value ?: return
        if (songs.isEmpty()) return
        if (controller.mediaItemCount == 0) {
            startFromEmptyQueue(songs)
            return
        }
        originalQueue = originalQueue + songs
        controller.addMediaItems(songs.map { it.toMediaItem() })
    }

    fun playSongsNext(songs: List<Song>) {
        val controller = _controller.value ?: return
        if (songs.isEmpty()) return
        if (controller.mediaItemCount == 0) {
            startFromEmptyQueue(songs)
            return
        }
        val currentId = controller.currentMediaItem?.toSong()?.id
        val logicalIndex = originalQueue.indexOfFirst { it.id == currentId }
        val insertAt = if (logicalIndex >= 0) logicalIndex + 1 else originalQueue.size
        originalQueue = originalQueue.take(insertAt) + songs + originalQueue.drop(insertAt)
        controller.addMediaItems(controller.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
    }

    private fun startFromEmptyQueue(songs: List<Song>) {
        playSongs(songs, 0, shuffleActive)
    }

    fun toggleFavorite(song: Song) {
        val favorite = !_favorites.value.contains(song.id)
        viewModelScope.launch {
            persistence.setFavorite(song, favorite)
        }
    }

    fun createPlaylist(name: String) {
        val playlistName = name.trim()
        if (playlistName.isEmpty()) return
        viewModelScope.launch {
            persistence.createPlaylist(playlistName)
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            persistence.addSongToPlaylist(playlistId, song)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val application = getApplication<Application>()
            try {
                val deleted = application.contentResolver.delete(song.uri, null, null)
                if (deleted > 0) {
                    application.contentResolver.notifyChange(song.uri, null)
                }
            } catch (_: Exception) {
            } finally {
                cleanupDeletedSong(song)
            }
        }
    }

    fun onSongDeleted(song: Song) {
        viewModelScope.launch {
            cleanupDeletedSong(song)
        }
    }

    fun updateSongMetadata(
        song: Song,
        title: String,
        artist: String,
        album: String,
        coverUri: Uri? = null
    ) {
        val newTitle = title.trim().ifEmpty { song.title }
        val newArtist = artist.trim().ifEmpty { song.artist }
        val newAlbum = album.trim().ifEmpty { song.album }
        val metadataChanged =
            newTitle != song.title || newArtist != song.artist || newAlbum != song.album
        if (!metadataChanged && coverUri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val application = getApplication<Application>()
            var coverFile: java.io.File? = null
            if (coverUri != null) {
                try {
                    CoverStore.save(application, song.id, coverUri)
                    coverFile = CoverStore.file(application, song.id)
                } catch (_: Exception) {
                }
            }
            if (metadataChanged || coverFile != null) {
                try {
                    AudioTagWriter.apply(
                        application,
                        song,
                        newTitle,
                        newArtist,
                        newAlbum,
                        coverFile
                    )
                } catch (_: Exception) {
                }
            }
            if (metadataChanged) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, newTitle)
                    put(MediaStore.Audio.Media.ARTIST, newArtist)
                    put(MediaStore.Audio.Media.ALBUM, newAlbum)
                }
                var success = false
                try {
                    success = application.contentResolver.update(song.uri, values, null, null) > 0
                    if (success) {
                        application.contentResolver.notifyChange(song.uri, null)
                    }
                } catch (_: Exception) {
                    success = false
                }
                if (success) {
                    updatePlayerMediaItem(
                        song.copy(title = newTitle, artist = newArtist, album = newAlbum)
                    )
                }
            }
            persistence.updateSongMetadata(song.id, newTitle, newArtist, newAlbum)
            LibraryEvents.requestLibraryReload()
        }
    }

    fun hasOriginalCover(song: Song): Boolean =
        AudioTagWriter.hasOriginalCover(getApplication(), song.id)

    fun restoreSongCover(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val application = getApplication<Application>()
            try {
                AudioTagWriter.restoreOriginal(application, song)
            } catch (_: Exception) {
            }
            CoverStore.remove(application, song.id)
            LibraryEvents.requestLibraryReload()
        }
    }

    private fun updatePlayerMediaItem(updated: Song) {
        originalQueue = originalQueue.map { if (it.id == updated.id) updated else it }
        _controller.value?.takeIf { it.mediaItemCount > 0 }?.let { player ->
            val index = (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == updated.id.toString()
            }
            if (index != null && player.getMediaItemAt(index).localConfiguration?.uri == updated.uri) {
                player.replaceMediaItem(index, updated.toMediaItem())
            }
        }
    }

    private suspend fun cleanupDeletedSong(song: Song) {
        _controller.value?.takeIf { it.mediaItemCount > 0 }?.let { player ->
            val index = (0 until player.mediaItemCount).firstOrNull {
                player.getMediaItemAt(it).mediaId == song.id.toString()
            }
            if (index != null) {
                player.removeMediaItem(index)
            }
        }
        originalQueue = originalQueue.filterNot { it.id == song.id }
        persistence.setFavorite(song, false)
        persistence.removeFromHistory(song.id)
        persistence.removeSongFromPlaylists(song.id)
        LibraryEvents.requestLibraryReload()
    }

    override fun onCleared() {
        paletteJob?.cancel()
        // The listener was added in the connect callback, so it has to come off before the future is
        // released; otherwise it keeps a reference to this cleared ViewModel.
        _controller.value?.removeListener(listener)
        _controller.value?.release()
        _controller.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "PlayerController"
    }
}
