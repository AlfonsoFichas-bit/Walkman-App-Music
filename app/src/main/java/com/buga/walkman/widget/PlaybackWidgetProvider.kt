@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.buga.walkman.widget

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.buga.walkman.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture

/**
 * Receives widget taps and asks for a re-render.
 *
 * Track-change pushes deliberately live in [PlaybackService], not here. A widget outlives the process,
 * so a provider-only design left the launcher showing a stale cover whenever Android killed the process
 * and recreated it for the foreground service. Because the service is alive exactly while audio is
 * playing, it is the reliable place to observe transitions.
 *
 * The [Connection] below is process-scoped, as the previous statics were, but it is now actually
 * released when the last widget instance goes away, so an idle widget no longer pins the playback
 * service open.
 */
class PlaybackWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRenderer.primeWidgetCount(context.applicationContext)
        // First instance appeared: get a render on screen without waiting for playback to change.
        connect(context.applicationContext) { controller ->
            WidgetRenderer.render(context.applicationContext, controller)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetRenderer.primeWidgetCount(context.applicationContext)
        connect(context.applicationContext) { controller ->
            WidgetRenderer.render(context.applicationContext, controller)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        connect(context.applicationContext) { controller ->
            WidgetRenderer.render(context.applicationContext, controller)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in CONTROL_ACTIONS) return
        connect(context.applicationContext) { controller ->
            when (action) {
                ACTION_PLAY_PAUSE ->
                    if (controller.isPlaying) controller.pause() else controller.play()
                ACTION_NEXT -> controller.seekToNextMediaItem()
                ACTION_PREVIOUS -> controller.seekToPreviousMediaItem()
            }
            // No fixed delay: the service pushes the resulting render from
            // Player.Listener.onEvents, which is strictly more reliable than guessing a wait time.
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        releaseIfUnused(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Connection.release()
        WidgetRenderer.shutdown()
    }

    private fun releaseIfUnused(context: Context) {
        if (WidgetRenderer.widgetIds(context).isNotEmpty()) return
        Connection.release()
        WidgetRenderer.shutdown()
    }

    private fun connect(context: Context, onConnected: (MediaController) -> Unit) {
        Connection.controller?.let {
            onConnected(it)
            return
        }
        if (Connection.future == null) {
            Connection.future = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java))
            ).buildAsync()
        }
        Connection.future?.addListener({
            val controller = try {
                Connection.future?.get()
            } catch (_: Exception) {
                null
            } ?: return@addListener
            if (Connection.controller == null) {
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        WidgetRenderer.render(context, player)
                    }
                }
                Connection.controller = controller
                Connection.listener = listener
                controller.addListener(listener)
            }
            onConnected(controller)
        }, ContextCompat.getMainExecutor(context))
    }

    private object Connection {
        var controller: MediaController? = null
        var listener: Player.Listener? = null
        var future: ListenableFuture<MediaController>? = null

        fun release() {
            listener?.let { controller?.removeListener(it) }
            controller?.release()
            controller = null
            listener = null
            future?.let { MediaController.releaseFuture(it) }
            future = null
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.buga.walkman.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.buga.walkman.widget.NEXT"
        const val ACTION_PREVIOUS = "com.buga.walkman.widget.PREVIOUS"

        private val CONTROL_ACTIONS = setOf(ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS)
    }
}
