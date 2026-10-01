package com.buga.walkman.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.buga.walkman.data.db.FolderState
import com.buga.walkman.model.Album
import com.buga.walkman.model.Artist
import com.buga.walkman.model.Song

class MediaRepository(private val context: Context) {

    fun loadSongs(): List<Song> =
        loadSongs(null, null) {
            "${MediaStore.Audio.Media.TITLE} ASC"
        }

    fun loadSongsByAlbum(albumId: Long): List<Song> =
        loadSongs("${MediaStore.Audio.Media.ALBUM_ID} = ?", arrayOf(albumId.toString())) {
            "${MediaStore.Audio.Media.TRACK} ASC"
        }

    fun loadSongsByArtist(artistId: Long): List<Song> =
        loadSongs("${MediaStore.Audio.Media.ARTIST_ID} = ?", arrayOf(artistId.toString())) {
            "${MediaStore.Audio.Media.ALBUM} ASC, ${MediaStore.Audio.Media.TRACK} ASC"
        }

    fun loadAlbumsForSongs(songs: List<Song>): List<Album> {
        val albumIds = songs.map { it.albumId }.filter { it > 0 }.distinct()
        if (albumIds.isEmpty()) return emptyList()
        val placeholders = albumIds.joinToString(",") { "?" }
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.FIRST_YEAR
        )
        val albums = mutableListOf<Album>()
        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Albums._ID} IN ($placeholders)",
            albumIds.map { it.toString() }.toTypedArray(),
            "${MediaStore.Audio.Albums.ALBUM} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                albums += Album(
                    id = cursor.getLong(0),
                    title = cursor.getString(1) ?: context.getString(com.buga.walkman.R.string.unknown_album),
                    artist = cursor.getString(2) ?: context.getString(com.buga.walkman.R.string.unknown_artist),
                    songCount = cursor.getInt(3),
                    year = cursor.getInt(4)
                )
            }
        }
        return albums
    }

    fun loadArtistsForSongs(songs: List<Song>): List<Artist> {
        val artistIds = songs.map { it.artistId }.filter { it > 0 }.distinct()
        if (artistIds.isEmpty()) return emptyList()
        val placeholders = artistIds.joinToString(",") { "?" }
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        )
        val artists = mutableListOf<Artist>()
        context.contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Artists._ID} IN ($placeholders)",
            artistIds.map { it.toString() }.toTypedArray(),
            "${MediaStore.Audio.Artists.ARTIST} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                artists += Artist(
                    id = cursor.getLong(0),
                    name = cursor.getString(1) ?: context.getString(com.buga.walkman.R.string.unknown_artist),
                    albumCount = cursor.getInt(2),
                    songCount = cursor.getInt(3)
                )
            }
        }
        return artists
    }

    private fun loadSongs(selection: String?, args: Array<String>?, sortOrder: () -> String): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val songs = mutableListOf<Song>()

        var baseSelection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')"
        var baseArgs = emptyArray<String>()

        val folderPaths = FolderState.absolutePaths
        if (folderPaths.isNotEmpty()) {
            val pathFilter = folderPaths.joinToString(" OR ") {
                "(${MediaStore.Audio.Media.DATA} = ? OR ${MediaStore.Audio.Media.DATA} LIKE ?)"
            }
            baseSelection += " AND ($pathFilter)"
            baseArgs = folderPaths.flatMap { listOf(it, "$it/%") }.toTypedArray()
        }

        val finalSelection = selection?.let { "$it AND ($baseSelection)" } ?: baseSelection
        val finalArgs = (args ?: emptyArray()) + baseArgs

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            finalSelection,
            finalArgs,
            sortOrder()
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                songs += Song(
                    id = cursor.getLong(0),
                    title = cursor.getString(1) ?: context.getString(com.buga.walkman.R.string.unknown_title),
                    artist = cursor.getString(2) ?: context.getString(com.buga.walkman.R.string.unknown_artist),
                    album = cursor.getString(3) ?: context.getString(com.buga.walkman.R.string.unknown_album),
                    albumId = cursor.getLong(4),
                    artistId = cursor.getLong(5),
                    duration = cursor.getInt(6),
                    trackNumber = cursor.getInt(7),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)),
                    mimeType = cursor.getString(9),
                    dataPath = cursor.getString(8)
                )
            }
        }
        return songs
    }
}