package com.buga.walkman.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.Coil
import coil.request.ImageRequest
import com.buga.walkman.MainActivity
import com.buga.walkman.R
import com.buga.walkman.data.media.ArtworkColorExtractor
import com.buga.walkman.model.Song
import com.buga.walkman.model.toSong
import com.buga.walkman.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appContext = context.applicationContext
        appContextRef = appContext
        cachedController?.let { renderNow(appContext, it) }
        connect(appContext) { controller -> renderNow(appContext, controller) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS -> {
                val appContext = context.applicationContext
                appContextRef = appContext
                connect(appContext) { controller ->
                    when (intent.action) {
                        ACTION_PLAY_PAUSE -> {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                        ACTION_NEXT -> controller.seekToNext()
                        ACTION_PREVIOUS -> controller.seekToPrevious()
                    }
                    scope.launch {
                        delay(120)
                        renderNow(appContext, controller)
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val remaining = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            stopTicker()
        }
    }

    override fun onDisabled(context: Context) {
        stopTicker()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        appContextRef = context.applicationContext
        cachedController?.let { renderNow(context.applicationContext, it) }
    }

    private fun renderNow(context: Context, controller: MediaController?) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(context, PlaybackWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        ids.forEach { id ->
            val views = buildViews(context, controller, coverSideDp(context, appWidgetManager, id))
            appWidgetManager.updateAppWidget(id, views)
        }
        applyDynamicTints(context, controller)
        manageTicker(context, controller?.isPlaying == true)
    }

    private fun buildViews(
        context: Context,
        controller: MediaController?,
        coverSideDp: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.playback_widget)
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            controlPendingIntent(context, ACTION_PREVIOUS, REQUEST_PREVIOUS)
        )
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            controlPendingIntent(context, ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE)
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            controlPendingIntent(context, ACTION_NEXT, REQUEST_NEXT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            views.setViewLayoutWidth(R.id.widget_cover, coverSideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_cover, coverSideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        val song = controller?.currentMediaItem?.toSong()
        if (song == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_placeholder_title))
            views.setTextViewText(R.id.widget_artist, "")
            views.setImageViewResource(R.id.widget_cover, R.drawable.widget_note_placeholder)
            views.setImageViewResource(R.id.widget_play_pause, R.drawable.widget_icon_play)
            views.setProgressBar(R.id.widget_progress, 1000, 0, false)
            return views
        }

        views.setTextViewText(R.id.widget_title, song.title)
        views.setTextViewText(R.id.widget_artist, song.artist)
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (controller.isPlaying) R.drawable.widget_icon_pause else R.drawable.widget_icon_play
        )
        views.setImageViewResource(R.id.widget_cover, R.drawable.widget_note_placeholder)
        loadArtwork(context, views, song)

        val duration = controller.duration
        val max = if (duration == C.TIME_UNSET || duration <= 0) 1000 else duration.toInt()
        val progress = if (max == 1000) {
            0
        } else {
            controller.currentPosition.coerceIn(0L, duration).toInt()
        }
        views.setProgressBar(R.id.widget_progress, max, progress, false)
        return views
    }

    private fun coverSideDp(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ): Int {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val heightDp = if (minHeightDp > 0) minHeightDp else DEFAULT_COVER_DP
        return (heightDp - COVER_TOP_MARGIN_DP - PROGRESS_HEIGHT_DP - COVER_BOTTOM_MARGIN_DP)
            .coerceAtLeast(DEFAULT_COVER_DP)
    }

    private fun applyDynamicTints(context: Context, controller: MediaController?) {
        val song = controller?.currentMediaItem?.toSong() ?: return
        val songId = song.id
        scope.launch {
            val colors = artworkExtractor(context).colorsFor(song.albumId, song.albumArtUri)
            val currentSongId = cachedController?.currentMediaItem?.toSong()?.id
            if (currentSongId != songId) return@launch
            val views = RemoteViews(context.packageName, R.layout.playback_widget)
            views.setColorStateList(
                R.id.widget_root,
                "setBackgroundTintList",
                ColorStateList.valueOf(overlayBlack(colors.bottom.toArgb(), BACKGROUND_BLACK_FILTER))
            )
            views.setColorStateList(
                R.id.widget_play_pause,
                "setBackgroundTintList",
                ColorStateList.valueOf(colors.highlight.toArgb())
            )
            views.setColorStateList(
                R.id.widget_progress,
                "setProgressTintList",
                ColorStateList.valueOf(colors.highlight.toArgb())
            )
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, PlaybackWidgetProvider::class.java)
            )
            ids.forEach { appWidgetManager.updateAppWidget(it, views) }
        }
    }

    private fun overlayBlack(color: Int, alpha: Float): Int {
        val r = (android.graphics.Color.red(color) * (1f - alpha)).toInt()
        val g = (android.graphics.Color.green(color) * (1f - alpha)).toInt()
        val b = (android.graphics.Color.blue(color) * (1f - alpha)).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun connect(context: Context, onConnected: (MediaController) -> Unit) {
        cachedController?.let {
            onConnected(it)
            return
        }
        if (controllerFuture == null) {
            controllerFuture = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java))
            ).buildAsync()
        }
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                if (!controllerRegistered) {
                    controllerRegistered = true
                    controller.addListener(controllerListener)
                }
                cachedController = controller
                onConnected(controller)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private val controllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            uiContext()?.let { context ->
                cachedController?.let {
                    renderNow(context, it)
                    manageTicker(context, isPlaying)
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            uiContext()?.let { context ->
                cachedController?.let {
                    renderNow(context, it)
                    manageTicker(context, it.isPlaying)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            uiContext()?.let { context ->
                cachedController?.let { renderNow(context, it) }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            uiContext()?.let { context ->
                cachedController?.let { renderNow(context, it) }
            }
        }
    }

    private fun loadArtwork(context: Context, views: RemoteViews, song: Song) {
        val songId = song.id
        Coil.imageLoader(context).enqueue(
            ImageRequest.Builder(context)
                .data(song.albumArtUri)
                .size(96)
                .target { drawable ->
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    if (bitmap == null) return@target
                    val currentSongId = cachedController?.currentMediaItem?.toSong()?.id
                    if (currentSongId != songId) return@target
                    views.setImageViewBitmap(R.id.widget_cover, bitmap)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val ids = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, PlaybackWidgetProvider::class.java)
                    )
                    ids.forEach { appWidgetManager.updateAppWidget(it, views) }
                }
                .build()
        )
    }

    private fun updateProgress(context: Context) {
        val controller = cachedController ?: return
        if (!controller.isPlaying) {
            stopTicker()
            return
        }
        val duration = controller.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val views = RemoteViews(context.packageName, R.layout.playback_widget)
        views.setProgressBar(
            R.id.widget_progress,
            duration.toInt(),
            controller.currentPosition.coerceIn(0L, duration).toInt(),
            false
        )
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(context, PlaybackWidgetProvider::class.java)
        )
        ids.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    private fun manageTicker(context: Context, playing: Boolean) {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
        if (ids.isEmpty()) {
            stopTicker()
            return
        }
        if (!playing) {
            stopTicker()
            return
        }
        if (tickerJob == null) {
            tickerJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    updateProgress(context)
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun controlPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlaybackWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.buga.walkman.widget.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.buga.walkman.widget.NEXT"
        private const val ACTION_PREVIOUS = "com.buga.walkman.widget.PREVIOUS"

        private const val REQUEST_PLAY_PAUSE = 1
        private const val REQUEST_NEXT = 2
        private const val REQUEST_PREVIOUS = 3

        private const val COVER_TOP_MARGIN_DP = 0
        private const val COVER_BOTTOM_MARGIN_DP = 0
        private const val PROGRESS_HEIGHT_DP = 3
        private const val DEFAULT_COVER_DP = 48
        private const val BACKGROUND_BLACK_FILTER = 0.78f

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        @Volatile
        private var appContextRef: Context? = null

        @Volatile
        private var cachedController: MediaController? = null

        private var controllerFuture: ListenableFuture<MediaController>? = null
        private var controllerRegistered = false
        private var tickerJob: Job? = null
        private var artworkColors: ArtworkColorExtractor? = null

        private fun artworkExtractor(context: Context): ArtworkColorExtractor =
            artworkColors ?: ArtworkColorExtractor(context).also { artworkColors = it }

        private fun uiContext(): Context? = appContextRef
    }
}