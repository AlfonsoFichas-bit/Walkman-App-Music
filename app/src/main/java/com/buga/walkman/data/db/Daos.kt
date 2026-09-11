package com.buga.walkman.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT * FROM favorites WHERE songId = :songId")
    fun observeIsFavorite(songId: Long): Flow<FavoriteSongEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: FavoriteSongEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun observeQueue(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue ORDER BY position ASC")
    suspend fun loadQueue(): List<QueueItemEntity>

    @Query("DELETE FROM queue")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)
}

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playCount DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int = 5): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE songId = :songId")
    suspend fun get(songId: Long): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE songId = :songId")
    suspend fun remove(songId: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY rowid ASC")
    fun observeFolders(): Flow<List<SelectedFolder>>

    @Query("SELECT * FROM folders ORDER BY rowid ASC")
    suspend fun getFoldersSync(): List<SelectedFolder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(folder: SelectedFolder)

    @Query("DELETE FROM folders WHERE treeUri = :treeUri")
    suspend fun delete(treeUri: String)

    @Query("DELETE FROM folders")
    suspend fun clear()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long
}

@Dao
interface PlaylistSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun remove(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    suspend fun removeSong(songId: Long)
}