package com.buga.walkman.model

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.buga.walkman.data.CoverStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = 0L,
    val artistId: Long = 0L,
    val duration: Int = 0,
    val trackNumber: Int = 0,
    val uri: Uri = Uri.EMPTY,
    val mimeType: String? = null,
    val dataPath: String? = null
) {
    /**
     * Resolves through [CoverStore], which holds only the application context. Previously this was a
     * top-level `var` assigned by `MainActivity`, which retained the whole Activity for the lifetime
     * of the process and left the widget unable to resolve custom covers before the app was opened.
     */
    val albumArtUri: Uri
        get() = CoverStore.uriFor(id)
            ?: ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

    fun formatDuration(): String {
        val minutes = duration / 1000 / 60
        val seconds = (duration / 1000) % 60
        return "%d:%02d".format(minutes, seconds)
    }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int
) {
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(ALBUM_ART_BASE, id)
}

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long
)

const val EXTRA_SONG_ID = "walkman.song_id"
const val EXTRA_ALBUM_ID = "walkman.album_id"
const val EXTRA_ARTIST_ID = "walkman.artist_id"
const val EXTRA_DURATION_MS = "walkman.duration_ms"
const val EXTRA_TRACK_NUMBER = "walkman.track_number"

/**
 * MediaStore stopped serving artwork from this provider in API 29. It stays as the last-resort
 * fallback for API 27-28; on API 29+ callers should use `ContentResolver.loadThumbnail` with the
 * track's own content URI, which is what the widget does.
 */
private val ALBUM_ART_BASE = "content://media/external/audio/albumart".toUri()

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri)
            .setExtras(
                Bundle().apply {
                    putLong(EXTRA_SONG_ID, id)
                    putLong(EXTRA_ALBUM_ID, albumId)
                    putLong(EXTRA_ARTIST_ID, artistId)
                    putInt(EXTRA_DURATION_MS, duration)
                    putInt(EXTRA_TRACK_NUMBER, trackNumber)
                }
            )
            .build()
    )
    .build()

fun MediaItem.toSong(): Song {
    val extras = mediaMetadata.extras
    return Song(
        id = extras?.getLong(EXTRA_SONG_ID) ?: mediaId.toLongOrNull() ?: 0L,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
        albumId = extras?.getLong(EXTRA_ALBUM_ID) ?: 0L,
        artistId = extras?.getLong(EXTRA_ARTIST_ID) ?: 0L,
        duration = extras?.getInt(EXTRA_DURATION_MS) ?: 0,
        trackNumber = extras?.getInt(EXTRA_TRACK_NUMBER) ?: 0,
        uri = localConfiguration?.uri ?: Uri.EMPTY
    )
}
