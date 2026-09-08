package com.buga.walkman.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteSongEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val duration: Int,
    val trackNumber: Int,
    val uri: String,
    val addedAt: Long
)

@Entity(tableName = "queue")
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val duration: Int,
    val trackNumber: Int,
    val uri: String
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val duration: Int,
    val trackNumber: Int,
    val uri: String,
    val lastPlayedAt: Long,
    val playCount: Int
)

@Entity(tableName = "folders")
data class SelectedFolder(
    @PrimaryKey val id: Long = 1L,
    val treeUri: String,
    val displayName: String,
    val relativePath: String? = null,
    val absolutePath: String? = null
)