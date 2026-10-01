@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.buga.walkman.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.C
import androidx.media3.common.Player
import com.buga.walkman.MainActivity
import com.buga.walkman.R
import com.buga.walkman.data.CoverStore
import com.buga.walkman.data.media.ArtworkColorExtractor
import com.buga.walkman.model.Song
import com.buga.walkman.model.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns every `RemoteViews` push for the playback widget.
 *
 * [PlaybackService] and [PlaybackWidgetProvider] both render through this object, so there is a single
 * code path and a single artwork cache. The service is the long-lived process owner and therefore the
 * one that pushes on track changes; the provider only renders on demand and releases its controller.
 */
object WidgetRenderer {

    /**
     * Owns the mutable widget state, so every [Player] read has to happen here. ExoPlayer verifies its
     * own application thread and throws `IllegalStateException` on any access from elsewhere.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Binder work only, so the once-per-second progress push never blocks the main thread. */
    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val artworkCache = object : LruCache<Long, Bitmap>(ARTWORK_CACHE_SIZE) {
        override fun sizeOf(key: Long, value: Bitmap): Int = 1
    }

    private var artworkExtractor: ArtworkColorExtractor? = null

    /** Song currently displayed. Guards artwork and tint results that resolve for a stale request. */
    @Volatile
    private var displayedSongId: Long = NO_SONG

    private var artworkJob: Job? = null
    private var tintJob: Job? = null

    fun artworkExtractorFor(context: Context): ArtworkColorExtractor =
        artworkExtractor ?: ArtworkColorExtractor(context.applicationContext)
            .also { artworkExtractor = it }

    /**
     * Cached instance count. [hasWidgets] answers from this because `getAppWidgetIds` is a binder call
     * and the playback service asks on every player event, on the main thread.
     */
    @Volatile
    private var cachedWidgetCount = UNKNOWN_WIDGET_COUNT

    /** Querying the ids also refreshes [cachedWidgetCount], so the cache never needs its own sync path. */
    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
            .also { cachedWidgetCount = it.size }

    fun primeWidgetCount(context: Context) {
        widgetIds(context)
    }

    fun hasWidgets(): Boolean = cachedWidgetCount > 0

    /**
     * Rebuilds the whole widget and pushes it to every instance, then refreshes the dynamic tints.
     *
     * The [Player] is only touched inside the main-dispatcher scope, so this stays correct no matter
     * which thread calls it.
     */
    fun render(context: Context, player: Player?) {
        val appContext = context.applicationContext
        scope.launch {
            val song = player?.currentMediaItem?.toSong()
            displayedSongId = song?.id ?: NO_SONG
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = widgetIds(appContext)
            if (ids.isEmpty()) return@launch
            ids.forEach { id ->
                manager.updateAppWidget(id, buildViews(appContext, player, song, coverSideDp(manager, id)))
            }
            if (song != null) applyDynamicTints(appContext, player, song)
        }
    }

    /**
     * Progress-only push. Deliberately leaves the cover and the text untouched so it can run every
     * second without fighting a concurrent full render.
     *
     * Takes a snapshot of the position instead of a [Player] on purpose: ExoPlayer verifies that every
     * access happens on the thread that created it, so the caller has to read the value on its own
     * thread and hand over plain primitives. The binder push itself runs off the main thread.
     */
    fun renderProgress(context: Context, max: Int, progress: Int) {
        val appContext = context.applicationContext
        pushScope.launch {
            val ids = widgetIds(appContext)
            if (ids.isEmpty()) return@launch
            val views = RemoteViews(appContext.packageName, R.layout.playback_widget)
            views.setProgressBar(R.id.widget_progress, max, progress, false)
            val manager = AppWidgetManager.getInstance(appContext)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }

    private fun buildViews(
        context: Context,
        player: Player?,
        song: Song?,
        coverSideDp: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.playback_widget)
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            controlPendingIntent(context, PlaybackWidgetProvider.ACTION_PREVIOUS, REQUEST_PREVIOUS)
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            controlPendingIntent(context, PlaybackWidgetProvider.ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            controlPendingIntent(context, PlaybackWidgetProvider.ACTION_NEXT, REQUEST_NEXT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            views.setViewLayoutWidth(R.id.widget_cover, coverSideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_cover, coverSideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }

        if (song == null || player == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_placeholder_title))
            views.setTextViewText(R.id.widget_artist, "")
            views.setImageViewResource(R.id.widget_cover, R.drawable.widget_note_placeholder)
            views.setImageViewResource(R.id.widget_play_pause, R.drawable.widget_icon_play)
            views.setBoolean(R.id.widget_play_pause, "setEnabled", true)
            views.setContentDescription(R.id.widget_play_pause, context.getString(R.string.widget_play))
            views.setViewVisibility(R.id.widget_loading_spinner, View.GONE)
            views.setViewVisibility(R.id.widget_play_pause, View.VISIBLE)
            views.setProgressBar(R.id.widget_progress, 1000, 0, false)
            return views
        }

        views.setTextViewText(R.id.widget_title, song.title)
        views.setTextViewText(R.id.widget_artist, song.artist)

        // Cached artwork goes in immediately. The placeholder is only used when there is genuinely
        // nothing to show, so the cover no longer blinks to placeholder on every track change.
        val cached = artworkCache.get(song.id)
        if (cached != null) {
            views.setImageViewBitmap(R.id.widget_cover, cached)
        } else if (!CoverStore.has(context, song.id)) {
            views.setImageViewResource(R.id.widget_cover, R.drawable.widget_note_placeholder)
        }
        requestArtwork(context, player, song)

        val duration = player.duration
        val max = if (duration == C.TIME_UNSET || duration <= 0) 1000 else duration.toInt()
        val progress = if (max == 1000) {
            0
        } else {
            player.currentPosition.coerceIn(0L, duration).toInt()
        }
        views.setProgressBar(R.id.widget_progress, max, progress, false)
        applyPlayState(context, views, player)
        return views
    }

    /**
     * Loads the cover off the main thread and re-pushes the *complete* view set when it arrives.
     *
     * The previous implementation reused a captured `RemoteViews` snapshot, so a slow image load could
     * re-apply stale text and progress on top of a newer render. Re-rendering keeps the widget
     * internally consistent, and the `displayedSongId` check discards results for skipped tracks.
     */
    private fun requestArtwork(context: Context, player: Player, song: Song) {
        if (artworkCache.get(song.id) != null) return
        if (artworkJob?.isActive == true && displayedSongId == song.id) return
        artworkJob?.cancel()
        val appContext = context.applicationContext
        artworkJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadArtwork(appContext, song) }
            if (bitmap != null) artworkCache.put(song.id, bitmap)
            if (displayedSongId != song.id) return@launch
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = widgetIds(appContext)
            if (ids.isEmpty()) return@launch
            ids.forEach { id ->
                manager.updateAppWidget(
                    id,
                    buildViews(appContext, player, song, coverSideDp(manager, id))
                )
            }
        }
    }

    private fun loadArtwork(context: Context, song: Song): Bitmap? {
        CoverStore.file(context, song.id).takeIf { it.exists() }?.let { file ->
            BitmapFactory.decodeFile(file.absolutePath)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && song.uri != Uri.EMPTY) {
            runCatching {
                context.contentResolver.loadThumbnail(song.uri, Size(COVER_PX, COVER_PX), null)
            }.getOrNull()?.let { return it }
        }
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, song.uri)
                retriever.embeddedPicture?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } finally {
                // AutoCloseable only exists from API 29, so release explicitly for minSdk 27.
                @Suppress("DEPRECATION")
                retriever.release()
            }
        }.getOrNull()
    }

    private fun applyPlayState(context: Context, views: RemoteViews, player: Player) {
        val buffering = player.playbackState == Player.STATE_BUFFERING
        views.setContentDescription(
            R.id.widget_loading_spinner,
            context.getString(R.string.widget_buffering)
        )
        if (buffering) {
            views.setViewVisibility(R.id.widget_loading_spinner, View.VISIBLE)
            views.setViewVisibility(R.id.widget_play_pause, View.INVISIBLE)
            views.setBoolean(R.id.widget_play_pause, "setEnabled", false)
        } else {
            val playing = player.isPlaying
            views.setViewVisibility(R.id.widget_loading_spinner, View.GONE)
            views.setViewVisibility(R.id.widget_play_pause, View.VISIBLE)
            views.setBoolean(R.id.widget_play_pause, "setEnabled", true)
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (playing) R.drawable.widget_icon_pause else R.drawable.widget_icon_play
            )
            views.setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (playing) R.string.widget_pause else R.string.widget_play)
            )
        }
    }

    private fun applyDynamicTints(context: Context, player: Player, song: Song) {
        val appContext = context.applicationContext
        val songId = song.id
        tintJob?.cancel()
        tintJob = scope.launch {
            val colors = artworkExtractorFor(appContext).colorsFor(song.albumId, song.albumArtUri)
            if (displayedSongId != songId) return@launch
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = widgetIds(appContext)
            if (ids.isEmpty()) return@launch
            val highlight = colors.highlight.toArgb()
            val views = RemoteViews(appContext.packageName, R.layout.playback_widget)
            // RemoteViews.setColorStateList only exists from API 31. Calling it on API 27-30 threw
            // NoSuchMethodError and took the widget down, so the tints are applied conditionally.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(
                    R.id.widget_root,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(overlayBlack(colors.bottom.toArgb(), BACKGROUND_BLACK_FILTER))
                )
                views.setColorStateList(
                    R.id.widget_play_pause,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(highlight)
                )
                views.setColorStateList(
                    R.id.widget_progress,
                    "setProgressTintList",
                    ColorStateList.valueOf(highlight)
                )
                views.setColorStateList(
                    R.id.widget_loading_spinner,
                    "setIndeterminateTintList",
                    ColorStateList.valueOf(highlight)
                )
            }
            applyPlayState(appContext, views, player)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }

    private fun overlayBlack(color: Int, alpha: Float): Int {
        val r = (android.graphics.Color.red(color) * (1f - alpha)).toInt()
        val g = (android.graphics.Color.green(color) * (1f - alpha)).toInt()
        val b = (android.graphics.Color.blue(color) * (1f - alpha)).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun coverSideDp(manager: AppWidgetManager, widgetId: Int): Int {
        val options = manager.getAppWidgetOptions(widgetId)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val heightDp = if (minHeightDp > 0) minHeightDp else DEFAULT_COVER_DP
        return (heightDp - COVER_TOP_MARGIN_DP - PROGRESS_HEIGHT_DP - COVER_BOTTOM_MARGIN_DP)
            .coerceAtLeast(DEFAULT_COVER_DP)
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun controlPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, PlaybackWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Drops cached covers and pending work once the last widget instance is gone. */
    fun shutdown() {
        artworkJob?.cancel()
        tintJob?.cancel()
        artworkJob = null
        tintJob = null
        artworkCache.evictAll()
        artworkExtractor = null
        displayedSongId = NO_SONG
    }

    const val NO_SONG = -1L
    private const val UNKNOWN_WIDGET_COUNT = 0
    private const val COVER_PX = 256
    private const val ARTWORK_CACHE_SIZE = 12
    private const val COVER_TOP_MARGIN_DP = 10
    private const val COVER_BOTTOM_MARGIN_DP = 10
    private const val PROGRESS_HEIGHT_DP = 3
    private const val DEFAULT_COVER_DP = 48
    private const val BACKGROUND_BLACK_FILTER = 0.78f
    private const val REQUEST_PLAY_PAUSE = 1
    private const val REQUEST_NEXT = 2
    private const val REQUEST_PREVIOUS = 3
}
