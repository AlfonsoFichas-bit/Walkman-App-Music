package com.buga.walkman.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buga.walkman.R
import com.buga.walkman.ui.components.MiniPlayerBar
import com.buga.walkman.viewmodel.PlayerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalkmanScaffold(
    playerState: PlayerUiState,
    isRootRoute: Boolean,
    isPlayerRoute: Boolean,
    baseRoute: String?,
    onToggleRail: () -> Unit,
    onOpenSearch: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    val showHero = isRootRoute && !isPlayerRoute && baseRoute == "home"

    Box(modifier = Modifier.fillMaxSize()) {
        if (showHero) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.6f to Color.Transparent,
                                        1f to Color.Black
                                    )
                                ),
                                blendMode = BlendMode.DstOut
                            )
                        }
                    }
            ) {
                Image(
                    painter = painterResource(R.drawable.landingpage_vinyl_record),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
            }
        }

        Scaffold(
            modifier = Modifier,
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (isRootRoute && !isPlayerRoute) {
                    val title = when (baseRoute) {
                        "home" -> stringResource(R.string.screen_home)
                        "library" -> stringResource(R.string.screen_library)
                        "queue" -> stringResource(R.string.screen_queue)
                        "playlists" -> stringResource(R.string.tab_playlists)
                        "settings" -> stringResource(R.string.screen_settings)
                        else -> stringResource(R.string.app_name)
                    }
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onToggleRail) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenSearch) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.cd_search),
                                    tint = if (showHero) Color.White else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = if (showHero) Color.White else MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = if (showHero) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = !isPlayerRoute && playerState.currentSong != null,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    MiniPlayerBar(
                        state = playerState,
                        onTogglePlayPause = onTogglePlayPause,
                        onClick = onNavigateToPlayer,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            }
        ) { innerPadding ->
            content(innerPadding)
        }
    }
}
