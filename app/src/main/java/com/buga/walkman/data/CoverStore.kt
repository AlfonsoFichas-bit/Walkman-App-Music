package com.buga.walkman.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.buga.walkman.WalkmanApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stores per-song custom cover art inside the app's private storage.
 * The chosen image is downscaled and compressed to keep disk usage small.
 */
object CoverStore {
    private const val COVER_DIR = "song_covers"
    private const val MAX_DIMENSION = 1024
    private const val QUALITY = 90

    /**
     * Application context, installed once by [WalkmanApplication]. Holding the application context is
     * correct; the previous design held a `MainActivity` instance in a static field, which retained the
     * whole Activity and its Compose tree for the life of the process.
     */
    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun file(context: Context, songId: Long): File =
        File(context.filesDir, COVER_DIR).resolve("$songId.jpg")

    fun resolve(context: Context, songId: Long): Uri? =
        file(context, songId).takeIf { it.exists() }?.toUri()

    fun has(context: Context, songId: Long): Boolean =
        file(context, songId).exists()

    /**
     * Resolves a custom cover without a [Context] argument, for callers such as `Song.albumArtUri` that
     * only have a song id. Returns null when the store is not installed or the song has no custom
     * cover, in which case the caller falls back to MediaStore.
     */
    fun uriFor(songId: Long): Uri? {
        val context = appContext ?: return null
        return resolve(context, songId)
    }

    suspend fun save(context: Context, songId: Long, source: Uri) {
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, COVER_DIR).apply { mkdirs() }
            val target = file(context, songId)
            val tmp = File(dir, "$songId.tmp")
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    val decoded = BitmapFactory.decodeStream(input) ?: return@use
                    val scaled = when {
                        decoded.width > MAX_DIMENSION || decoded.height > MAX_DIMENSION -> {
                            val scale = MAX_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height)
                            Bitmap.createScaledBitmap(
                                decoded,
                                (decoded.width * scale).toInt(),
                                (decoded.height * scale).toInt(),
                                true
                            )
                        }
                        else -> decoded
                    }
                    tmp.outputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
                    }
                    if (scaled !== decoded) scaled.recycle()
                    decoded.recycle()
                }
                target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                }
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    fun remove(context: Context, songId: Long) {
        file(context, songId).delete()
    }
}