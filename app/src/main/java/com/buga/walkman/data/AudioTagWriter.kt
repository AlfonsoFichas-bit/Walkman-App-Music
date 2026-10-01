package com.buga.walkman.data

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import com.buga.walkman.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

/**
 * Writes title/artist/album and embedded album art directly into the audio file
 * (mp3, m4a, flac, ogg, wav, wma, aiff...) using jaudiotagger, then asks
 * [MediaScannerConnection] to re-scan the file so MediaStore regenerates the row,
 * album and artist entries from the updated tags.
 *
 * Every operation works on a copy in cacheDir and pushes the result back through
 * [android.content.ContentResolver.openOutputStream] so it works with any per-item
 * write grant obtained via [android.provider.MediaStore.createWriteRequest].
 */
object AudioTagWriter {
    private const val ORIG_DIR = "song_covers_orig"
    private const val ART_MIME = "image/jpeg"

    fun hasOriginalCover(context: Context, songId: Long): Boolean =
        originalFile(context, songId).exists() && originalMimeFile(context, songId).exists()

    /**
     * Writes [newTitle]/[newArtist]/[newAlbum] and, when [customCover] is provided,
     * replaces the embedded album art. Text fields are only touched when they differ
     * from the current values; artwork is only touched when [customCover] is non-null.
     * Returns true when the file was rewritten.
     */
    suspend fun apply(
        context: Context,
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        customCover: File? = null
    ): Boolean {
        val metadataChanged =
            newTitle != song.title || newArtist != song.artist || newAlbum != song.album
        if (!metadataChanged && customCover == null) return false
        return withContext(Dispatchers.IO) {
            val audio = copyAudioToCache(context, song) ?: return@withContext false
            try {
                val audioFile = AudioFileIO.read(audio)
                val tag = audioFile.getTagOrCreateAndSetDefault()
                if (metadataChanged) {
                    tag.setField(FieldKey.TITLE, newTitle)
                    tag.setField(FieldKey.ARTIST, newArtist)
                    tag.setField(FieldKey.ALBUM, newAlbum)
                }
                var coverChanged = false
                if (customCover != null) {
                    val bytes = customCover.readBytes()
                    if (bytes.isNotEmpty()) {
                        try {
                            backupOriginalArt(context, song.id, tag)
                            tag.deleteArtworkField()
                            tag.setField(
                                ArtworkFactory.getNew().apply {
                                    setBinaryData(bytes)
                                    setMimeType(ART_MIME)
                                }
                            )
                            coverChanged = true
                        } catch (_: Exception) {
                            coverChanged = false
                        }
                    }
                }
                if (!metadataChanged && !coverChanged) {
                    false
                } else {
                    writeBack(context, song, audio, audioFile)
                }
            } catch (_: Exception) {
                false
            } finally {
                audio.delete()
            }
        }
    }

    /**
     * Replaces the embedded album art with the artwork that was present before the
     * first custom cover was written. Does nothing unless [apply] previously stored
     * a backup. Deletes the backup once restored.
     */
    suspend fun restoreOriginal(context: Context, song: Song): Boolean {
        if (!hasOriginalCover(context, song.id)) return false
        return withContext(Dispatchers.IO) {
            val audio = copyAudioToCache(context, song) ?: return@withContext false
            try {
                val audioFile = AudioFileIO.read(audio)
                val tag = audioFile.getTagOrCreateAndSetDefault()
                tag.deleteArtworkField()
                tag.setField(
                    ArtworkFactory.getNew().apply {
                        setBinaryData(originalFile(context, song.id).readBytes())
                        setMimeType(originalMimeFile(context, song.id).readText().ifBlank { ART_MIME })
                    }
                )
                val written = writeBack(context, song, audio, audioFile)
                if (written) {
                    originalFile(context, song.id).delete()
                    originalMimeFile(context, song.id).delete()
                }
                written
            } catch (_: Exception) {
                false
            } finally {
                audio.delete()
            }
        }
    }

    private fun copyAudioToCache(context: Context, song: Song): File? {
        val dir = File(context.cacheDir, "music_tag_edit").apply { mkdirs() }
        val audio = File(dir, "${song.id}.${extensionFor(song)}")
        return try {
            context.contentResolver.openInputStream(song.uri)?.use { input ->
                audio.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            audio
        } catch (_: Exception) {
            audio.delete()
            null
        }
    }

    private fun writeBack(context: Context, song: Song, audio: File, audioFile: AudioFile): Boolean {
        var success = false
        try {
            AudioFileIO.write(audioFile)
            context.contentResolver.openOutputStream(song.uri, "w")?.use { output ->
                audio.inputStream().use { input -> input.copyTo(output) }
            }
            rescan(context, song)
            success = true
        } catch (_: Exception) {
            success = false
        }
        return success
    }

    private fun rescan(context: Context, song: Song) {
        val path = resolveDataPath(context, song) ?: return
        try {
            // Re-extracts tags so MediaStore regenerates the media row, album and
            // artist entries (the albums table itself is read-only for apps).
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Exception) {
        }
    }

    private fun resolveDataPath(context: Context, song: Song): String? =
        song.dataPath?.takeIf { it.isNotBlank() } ?: runCatching {
            context.contentResolver.query(
                song.uri,
                arrayOf(MediaStore.Audio.Media.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    private fun backupOriginalArt(context: Context, songId: Long, tag: Tag) {
        if (hasOriginalCover(context, songId)) return
        val artwork = tag.getFirstArtwork() ?: return
        val bytes = artwork.binaryData ?: return
        val dir = File(context.filesDir, ORIG_DIR).apply { mkdirs() }
        originalFile(context, songId).writeBytes(bytes)
        originalMimeFile(context, songId).writeText(artwork.mimeType ?: ART_MIME)
    }

    private fun extensionFor(song: Song): String {
        val mime = song.mimeType ?: return song.dataPath?.substringAfterLast('.', "")?.lowercase()
            ?: "dat"
        return when {
            mime.startsWith("audio/mpeg") -> "mp3"
            mime.startsWith("audio/mp4") || mime == "audio/x-m4a" -> "m4a"
            mime == "audio/flac" || mime.startsWith("audio/x-flac") -> "flac"
            mime.startsWith("audio/ogg") || mime.startsWith("application/ogg") -> "ogg"
            mime == "audio/wav" || mime.startsWith("audio/x-wav") || mime.startsWith("audio/wave") -> "wav"
            mime.contains("wma") -> "wma"
            mime.contains("aiff") || mime.contains("aif") -> "aiff"
            mime.contains("ape") -> "ape"
            else -> song.dataPath?.substringAfterLast('.', "")?.lowercase() ?: "dat"
        }
    }

    private fun originalFile(context: Context, songId: Long): File =
        File(context.filesDir, ORIG_DIR).resolve("$songId.img")

    private fun originalMimeFile(context: Context, songId: Long): File =
        File(context.filesDir, ORIG_DIR).resolve("$songId.mime")
}