package com.buga.walkman.ui.theme

import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

val WalkmanGradientTop = Color(0xFF2D1B3D)
val WalkmanGradientBottom = Color(0xFF1A0F2E)
val WalkmanAccentHighlight = Color(0xFFB388FF)

private val WalkmanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B4FA3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6A3A7E),
    onPrimaryContainer = Color(0xFFF6E2FF),
    secondary = WalkmanGrey,
    onSecondary = Color(0xFF1A0F2E),
    secondaryContainer = Color(0xFF4A3B57),
    onSecondaryContainer = Color(0xFFE8DEF0),
    tertiary = WalkmanAccent,
    onTertiary = Color.White,
    background = WalkmanGradientBottom,
    onBackground = Color.White,
    surface = Color(0xFF241633),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF4A3B57),
    onSurfaceVariant = Color(0xFFCCC2D9),
    outline = Color(0xFF968F9F),
    error = Color(0xFFF2B8B5)
)

@Composable
fun WalkmanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> WalkmanDarkColorScheme
    }
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val activity = context as? androidx.activity.ComponentActivity
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
