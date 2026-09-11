package com.buga.walkman.data

import android.content.Context
import com.buga.walkman.data.db.AppDatabase
import com.buga.walkman.data.db.PlaylistEntity
import com.buga.walkman.data.db.toFavoriteEntity
import com.buga.walkman.data.db.toPlayHistoryEntity
import com.buga.walkman.data.db.toPlaylist
import com.buga.walkman.data.db.toPlaylistSongEntity
import com.buga.walkman.data.db.toQueueItemEntity
import com.buga.walkman.data.db.toSong
import com.buga.walkman.model.Playlist
import com.buga.walkman.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlayerPersistence(private val context: Context, private val database: AppDatabase) {

    private val prefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)

    fun observeFavorites(): Flow<List<Song>> =
        database.favoriteDao().observeFavorites().map { list -> list.map { it.toSong() } }

    fun observeRecent(): Flow<List<Song>> =
        database.playHistoryDao().observeRecent().map { list -> list.map { it.toSong() } }

    fun observeTopPlayed(): Flow<List<Song>> =
        database.playHistoryDao().observeMostPlayed(limit = 5).map { list -> list.map { it.toSong() } }

    fun observePlaylists(): Flow<List<Playlist>> =
        database.playlistDao().observePlaylists().map { list -> list.map { it.toPlaylist() } }

    suspend fun createPlaylist(name: String) {
        database.playlistDao().insert(PlaylistEntity(name = name))
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        database.playlistSongDao().insert(song.toPlaylistSongEntity(playlistId))
    }

    suspend fun removeSongFromPlaylists(songId: Long) {
        database.playlistSongDao().removeSong(songId)
    }

    suspend fun saveQueue(items: List<Song>) {
        val dao = database.queueDao()
        dao.clear()
        if (items.isNotEmpty()) {
            dao.insertAll(items.mapIndexed { index, song -> song.toQueueItemEntity(index) })
        }
    }

    suspend fun loadQueue(): List<Song> =
        database.queueDao().loadQueue().map { it.toSong() }

    fun savePosition(index: Int, positionMs: Long) {
        prefs.edit()
            .putInt(KEY_INDEX, index)
            .putLong(KEY_POSITION, positionMs)
            .apply()
    }

    fun loadPosition(): SavedPlaybackState = SavedPlaybackState(
        index = prefs.getInt(KEY_INDEX, 0),
        positionMs = prefs.getLong(KEY_POSITION, 0L)
    )

    fun saveShuffleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE, enabled).apply()
    }

    fun loadShuffleEnabled(): Boolean = prefs.getBoolean(KEY_SHUFFLE, false)

    suspend fun setFavorite(song: Song, favorite: Boolean) {
        val dao = database.favoriteDao()
        if (favorite) {
            dao.add(song.toFavoriteEntity())
        } else {
            dao.remove(song.id)
        }
    }

    suspend fun recordPlay(song: Song) {
        val dao = database.playHistoryDao()
        val existing = dao.get(song.id)
        dao.upsert(
            song.toPlayHistoryEntity(
                lastPlayedAt = System.currentTimeMillis(),
                playCount = (existing?.playCount ?: 0) + 1
            )
        )
    }

    suspend fun removeFromHistory(songId: Long) {
        database.playHistoryDao().remove(songId)
    }

    companion object {
        private const val KEY_SHUFFLE = "shuffle_enabled"
        private const val KEY_INDEX = "queue_index"
        private const val KEY_POSITION = "queue_position"
    }
}

data class SavedPlaybackState(
    val index: Int = 0,
    val positionMs: Long = 0L
)