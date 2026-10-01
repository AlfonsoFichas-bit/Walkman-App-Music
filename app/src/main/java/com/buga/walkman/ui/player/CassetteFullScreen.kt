package com.buga.walkman.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.buga.walkman.R
import com.buga.walkman.model.Song

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen cassette visualizer. Covers the whole screen while the current song is
 * playing; the back gesture/button returns to the normal player.
 */
@Composable
internal fun CassetteFullScreen(
    song: Song?,
    isPlaying: Boolean,
    currentIndex: Int,
    accentColor: Color,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            previousOrientation?.let { activity?.requestedOrientation = it }
        }
    }

    // Immersive mode: the clock, the icons and the gesture bar all sit on top of the cassette, so the
    // view is not full screen while any of them is visible. A swipe from an edge brings them back
    // transiently, which is why BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE and not a sticky hide.
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view) {
            val window = view.context.findActivity()?.window
            val controller = window?.let { WindowInsetsControllerCompat(it, view) }
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val density = LocalDensity.current
    val swipeThreshold = with(density) { 90.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF444444))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .pointerInput(onPrev, onNext, swipeThreshold) {
                    var accumulatedDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                accumulatedDrag > swipeThreshold -> onPrev()
                                accumulatedDrag < -swipeThreshold -> onNext()
                            }
                            accumulatedDrag = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            accumulatedDrag += dragAmount
                        }
                    )
                }
                .pointerInput(onPlayPause) {
                    detectTapGestures(onDoubleTap = { onPlayPause() })
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedCassette(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.58f)
                    .rotate(270f)
                    .scale(1.7f),
                isPlaying = isPlaying,
                labelAccentColor = accentColor,
                trackTitle = song?.title ?: "",
                artistText = song?.artist ?: "",
                sideText = if (currentIndex.coerceAtLeast(0) % 2 == 0) "A" else "B"
            )
        }

        // safeDrawing instead of statusBarsPadding: the bars are hidden, so this only keeps the
        // button clear of the display cutout and of the gesture area.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_navigate_up),
                tint = Color.White
            )
        }
    }
}