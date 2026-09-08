package com.buga.walkman.data.db

import com.buga.walkman.model.Song

fun Song.toFavoriteEntity(addedAt: Long = System.currentTimeMillis()) = FavoriteSongEntity(
    songId = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    duration = duration,
    trackNumber = trackNumber,
    uri = uri.toString(),
    addedAt = addedAt
)

fun FavoriteSongEntity.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    duration = duration,
    trackNumber = trackNumber,
    uri = android.net.Uri.parse(uri)
)

fun Song.toQueueItemEntity(position: Int) = QueueItemEntity(
    position = position,
    songId = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    duration = duration,
    trackNumber = trackNumber,
    uri = uri.toString()
)

fun QueueItemEntity.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    duration = duration,
    trackNumber = trackNumber,
    uri = android.net.Uri.parse(uri)
)

fun Song.toPlayHistoryEntity(
    lastPlayedAt: Long = System.currentTimeMillis(),
    playCount: Int = 1
) = PlayHistoryEntity(
    songId = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    duration = duration,
    trackNumber = trackNumber,
    uri = uri.toString(),
    lastPlayedAt = lastPlayedAt,
    playCount = playCount
)

fun PlayHistoryEntity.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    duration = duration,
    trackNumber = trackNumber,
    uri = android.net.Uri.parse(uri)
)