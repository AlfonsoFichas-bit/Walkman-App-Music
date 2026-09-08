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
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE id = 1")
    fun observeFolder(): Flow<SelectedFolder?>

    @Query("SELECT * FROM folders WHERE id = 1")
    suspend fun getFolderSync(): SelectedFolder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(folder: SelectedFolder)

    @Query("DELETE FROM folders WHERE id = 1")
    suspend fun clear()
}