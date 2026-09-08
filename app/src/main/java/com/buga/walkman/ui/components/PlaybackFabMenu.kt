package com.buga.walkman.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buga.walkman.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackFabMenu(
    accent: Color,
    onPlayAll: () -> Unit,
    onShuffle: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        if (onShuffle != null) {
            ExpressiveFabMenuItem(
                expanded = expanded,
                icon = Icons.Default.Shuffle,
                label = stringResource(R.string.shuffle),
                accent = accent,
                delayMillis = 120,
                onClick = {
                    expanded = false
                    onShuffle()
                }
            )
        }
        ExpressiveFabMenuItem(
            expanded = expanded,
            icon = Icons.Default.PlayArrow,
            label = stringResource(R.string.play_all),
            accent = accent,
            delayMillis = 0,
            onClick = {
                expanded = false
                onPlayAll()
            }
        )

        FloatingActionButton(
            onClick = { if (enabled) expanded = !expanded },
            modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
            containerColor = accent,
            contentColor = onAccent
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.cd_playback_options)
            )
        }
    }
}

@Composable
fun ExpressiveFabMenuItem(
    expanded: Boolean,
    icon: ImageVector,
    label: String,
    accent: Color,
    delayMillis: Int,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = delayMillis)) +
            scaleIn(
                initialScale = 0.6f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)) +
            scaleOut(targetScale = 0.6f, animationSpec = tween(durationMillis = 140))
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = accent.copy(alpha = 0.16f),
            contentColor = accent,
            shadowElevation = 3.dp,
            modifier = Modifier
                .padding(start = 16.dp, bottom = 8.dp)
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}