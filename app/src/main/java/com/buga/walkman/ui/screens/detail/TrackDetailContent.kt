package com.buga.walkman.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buga.walkman.R
import com.buga.walkman.model.Song
import com.buga.walkman.ui.components.CoverImage
import com.buga.walkman.ui.components.PlaybackFabMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackDetailContent(
    title: String,
    subtitle: String,
    metaLine: String,
    coverModel: Any?,
    tracks: List<Song>,
    onBack: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onShuffle: (() -> Unit)? = null,
    headerShape: Shape = MaterialTheme.shapes.extraLarge,
    headerBackground: Color? = null,
    headerPlaceholder: (@Composable () -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    tabs: List<String> = emptyList(),
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    albumGrid: (LazyListScope.() -> Unit)? = null
) {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = accent,
        activeContentColor = onAccent
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_up),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (headerContent != null) {
                            headerContent()
                        } else {
                            CoverImage(
                            model = coverModel,
                            contentDescription = title,
                            shape = headerShape,
                            placeholder = headerPlaceholder,
                            backgroundColor = headerBackground ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .aspectRatio(1f)
                        )

                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        }

                        if (tabs.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                SingleChoiceSegmentedButtonRow {
                                    tabs.forEachIndexed { index, label ->
SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                                    onClick = { onTabSelected(index) },
                                    selected = index == selectedTab,
                                    colors = segmentedColors
                                ) {
                                            Text(label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            if (selectedTab == 1 && albumGrid != null) {
                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                albumGrid()
            } else {
                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                itemsIndexed(tracks, key = { _, song -> song.id }) { index, song ->
                ListItem(
                    headlineContent = {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    trailingContent = {
                        Text(
                            text = song.formatDuration(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPlayAt(index) }
                )
                }
            }
        }
        }

        if (tabs.isEmpty() || selectedTab == 0) {
            PlaybackFabMenu(
                accent = accent,
                onPlayAll = { if (tracks.isNotEmpty()) onPlayAt(0) },
                onShuffle = { onShuffle?.invoke() },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }
}