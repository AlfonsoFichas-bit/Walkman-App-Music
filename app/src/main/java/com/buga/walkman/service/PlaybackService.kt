// Media3's Player and DefaultRenderersFactory are @UnstableApi. Opting in per file keeps the marker
// from propagating to callers, which annotating the class would have done.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.buga.walkman.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.buga.walkman.widget.WidgetRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /**
     * Scoped to the service, not the process. The widget ticker used to live in a process-wide scope.
     *
     * Dispatcher.Main is required, not a preference: ExoPlayer throws
     * `IllegalStateException: Player is accessed on the wrong thread` on any access from another
     * thread, and `MediaSessionService` builds the player on the main thread. The off-main part is the
     * binder push, which `WidgetRenderer` performs on its own scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scopeJob = scope.coroutineContext[Job]
    private var progressJob: Job? = null

    private val widgetListener = object : Player.Listener {
        /**
         * Single hook for every state change that can affect the widget: item transitions, play state,
         * isPlaying, timeline and position discontinuities. `onEvents` fires only when something
         * actually changed, so this stays cheap.
         */
        override fun onEvents(player: Player, events: Player.Events) {
            WidgetRenderer.render(this@PlaybackService, player)
            manageProgressTicker(player)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val renderersFactory = NextRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(widgetListener)
        mediaSession = MediaSession.Builder(this, player).build()

        // A widget can survive a process death, so bring it back in sync as soon as the service is up.
        WidgetRenderer.primeWidgetCount(this)
        WidgetRenderer.render(this, player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private fun manageProgressTicker(player: Player) {
        if (!player.isPlaying) {
            stopProgressTicker()
            return
        }
        if (progressJob?.isActive == true) return
        if (!WidgetRenderer.hasWidgets()) return
        // Samples the player here, on main, and hands WidgetRenderer plain primitives. Passing the
        // Player itself would move the read onto the push thread and crash.
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (!player.isPlaying) break
                val duration = player.duration
                if (duration == C.TIME_UNSET || duration <= 0) continue
                val position = player.currentPosition.coerceIn(0L, duration)
                WidgetRenderer.renderProgress(
                    this@PlaybackService,
                    duration.toInt(),
                    position.toInt()
                )
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onDestroy() {
        stopProgressTicker()
        // cancel(), not cancelAndJoin(): onDestroy is not a coroutine and must not block.
        scopeJob?.cancel()
        mediaSession?.run {
            player.removeListener(widgetListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1000L
    }
}
