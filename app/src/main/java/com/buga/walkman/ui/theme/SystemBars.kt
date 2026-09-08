package com.buga.walkman.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

private fun needsLightAppearance(background: Color): Boolean =
    background.luminance() >= 0.5f

@Composable
fun AdaptiveSystemBars(
    statusBarColor: Color,
    navigationBarColor: Color
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = needsLightAppearance(statusBarColor)
            controller.isAppearanceLightNavigationBars = needsLightAppearance(navigationBarColor)
        }
    }
}
