package com.buga.walkman.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.buga.walkman.R

private data class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun TrackActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    accentColor: Color? = null,
    darkOverlay: Color = Color(0x99000000),
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val playNextLabel = stringResource(R.string.play_next)
    val addToQueueLabel = stringResource(R.string.add_to_queue)
    val deleteLabel = stringResource(R.string.delete_song)

    val entries = buildList {
        if (onPlayNext != null) {
            add(MenuEntry(playNextLabel, Icons.Filled.SkipNext) { onPlayNext() })
        }
        if (onAddToQueue != null) {
            add(MenuEntry(addToQueueLabel, Icons.Filled.PlaylistAdd) { onAddToQueue() })
        }
        if (onDelete != null) {
            add(MenuEntry(deleteLabel, Icons.Filled.Delete) { onDelete() })
        }
    }

    ExpressiveItemsMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        accentColor = accentColor,
        darkOverlay = darkOverlay
    ) {
        entries.forEachIndexed { index, entry ->
            ExpressiveMenuItem(
                onClick = {
                    onDismissRequest()
                    entry.onClick()
                },
                text = { Text(entry.label) },
                index = index,
                count = entries.size,
                trailingIcon = {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(MenuDefaults.TrailingIconSize)
                    )
                }
            )
        }
    }
}