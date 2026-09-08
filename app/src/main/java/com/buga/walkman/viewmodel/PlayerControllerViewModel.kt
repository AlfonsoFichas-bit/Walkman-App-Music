package com.buga.walkman.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
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
import com.buga.walkman.data.PlayerPersistence
import com.buga.walkman.data.db.AppDatabase
import com.buga.walkman.data.media.ArtworkColorExtractor
import com.buga.walkman.model.EXTRA_DURATION_MS
import com.buga.walkman.model.Song
import com.buga.walkman.model.toMediaItem
import com.buga.walkman.model.toSong
import com.buga.walkman.service.PlaybackService
import com.buga.walkman.ui.theme.WalkmanAccentHighlight
import com.buga.walkman.ui.theme.WalkmanGradientBottom
import com.buga.walkman.ui.theme.WalkmanGradientTop
import com.google.common.util.concurrent.ListenableFuture
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

    private val _controller = MutableStateFlow<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var restorePending = false

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

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _state.update { it.copy(shuffleEnabled = enabled) }
            viewModelScope.launch { persistence.saveShuffleEnabled(enabled) }
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
        viewModelScope.launch {
            while (isActive) {
                _controller.value?.let { controller ->
                    if (controller.playbackState != Player.STATE_IDLE) {
                        val position = controller.currentPosition.coerceAtLeast(0)
                        val duration = controller.duration
                            .takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                        _state.update { it.copy(positionMs = position, durationMs = duration) }
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
            viewModelScope.launch {
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
        viewModelScope.launch {
            if (items.isEmpty() && restorePending) return@launch
            persistence.saveQueue(items)
        }
    }

    private fun restoreShuffle(controller: MediaController) {
        val saved = persistence.loadShuffleEnabled()
        if (controller.shuffleModeEnabled != saved) {
            controller.shuffleModeEnabled = saved
        } else {
            _state.update { it.copy(shuffleEnabled = controller.shuffleModeEnabled) }
        }
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
                controller.setMediaItems(items.map { it.toMediaItem() }, 0, 0L)
                controller.prepare()
            }
        }
    }

    private fun recordPlay(song: Song) {
        viewModelScope.launch {
            persistence.recordPlay(song)
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val controller = _controller.value ?: return
        controller.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
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
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = _controller.value ?: return
        controller.repeatMode = when (_state.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
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

    fun toggleFavorite(song: Song) {
        val favorite = !_favorites.value.contains(song.id)
        viewModelScope.launch {
            persistence.setFavorite(song, favorite)
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }

    companion object {
        private const val TAG = "PlayerController"
    }
}
