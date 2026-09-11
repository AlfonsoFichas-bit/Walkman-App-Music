package com.buga.walkman.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.buga.walkman.R
import com.buga.walkman.MainActivity
import com.buga.walkman.ui.theme.AdaptiveSystemBars
import com.buga.walkman.ui.WalkmanScreen
import com.buga.walkman.viewmodel.LibraryViewModel
import com.buga.walkman.viewmodel.PlayerControllerViewModel
import com.buga.walkman.ui.screens.detail.AlbumDetailScreen
import com.buga.walkman.ui.screens.detail.ArtistDetailScreen
import com.buga.walkman.ui.screens.HomeScreen
import com.buga.walkman.ui.screens.LibraryScreen
import com.buga.walkman.ui.screens.QueueScreen
import com.buga.walkman.ui.screens.SettingsScreen
import com.buga.walkman.viewmodel.SettingsViewModel
import com.buga.walkman.ui.screens.FolderSetupScreen
import com.buga.walkman.ui.screens.FavoritesScreen
import com.buga.walkman.ui.components.LocalPlayerCoverAccent
import com.buga.walkman.ui.screens.SearchScreen

private val ROOT_ROUTES = setOf("home", "library", "queue", "settings")

@Composable
fun WalkmanApp() {
    val navController = rememberNavController()

    val playerViewModel: PlayerControllerViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val playerState by playerViewModel.state.collectAsState()

    val folderReady by settingsViewModel.folderReady.collectAsState()
    val configFolders by settingsViewModel.folders.collectAsState()
    val setupRequired = folderReady && configFolders.isEmpty()

    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    val openPlayerRequest = mainActivity?.pendingOpenPlayer == true

    var playerExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest) {
            playerExpanded = true
            mainActivity?.consumeOpenPlayer()
        }
    }

    var showRail by remember { mutableStateOf(false) }
    var libraryTab by rememberSaveable { mutableIntStateOf(0) }

    WalkmanPermissionHandler(
        onPermissionGranted = { libraryViewModel.loadAll() }
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val baseRoute = currentRoute?.substringBefore('?')
    val isRootRoute = baseRoute in ROOT_ROUTES
    val isPlayerRoute = false

    val navItems = listOf(
        NavItem("library", 0, Icons.Filled.MusicNote, Icons.Outlined.MusicNote, stringResource(R.string.tab_songs)),
        NavItem("library", 1, Icons.Filled.Album, Icons.Outlined.Album, stringResource(R.string.tab_albums)),
        NavItem("library", 2, Icons.Filled.Group, Icons.Outlined.Group, stringResource(R.string.tab_artists)),
        NavItem("library", 3, Icons.AutoMirrored.Filled.PlaylistPlay, Icons.AutoMirrored.Filled.PlaylistPlay, stringResource(R.string.tab_playlists)),
        NavItem("settings", -1, Icons.Filled.Settings, Icons.Outlined.Settings, stringResource(R.string.screen_settings))
    )

    val selectedIndex = navItems.indexOfFirst { item ->
        baseRoute == item.baseRoute && (item.tabIndex < 0 || item.tabIndex == libraryTab)
    }.coerceAtLeast(-1)

    fun toggleRail() { showRail = !showRail }

    val animatedTop by animateColorAsState(
        targetValue = playerState.gradientTop,
        animationSpec = tween(durationMillis = 500),
        label = "adaptiveTop"
    )
    val animatedBottom by animateColorAsState(
        targetValue = playerState.gradientBottom,
        animationSpec = tween(durationMillis = 500),
        label = "adaptiveBottom"
    )

    AdaptiveSystemBars(
        statusBarColor = animatedTop,
        navigationBarColor = animatedBottom
    )

    if (setupRequired) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(listOf(animatedTop, animatedBottom))
                )
        ) {
            FolderSetupScreen(
                onFolderPicked = { uri ->
                    settingsViewModel.addFolder(context, uri)
                }
            )
        }
        return
    }

    CompositionLocalProvider(LocalPlayerCoverAccent provides playerState.accentHighlight) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(listOf(animatedTop, animatedBottom))
            )
            .pointerInput(showRail) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { _, dragAmount ->
                        if (!showRail && dragAmount > 20f) {
                            showRail = true
                        } else if (showRail && dragAmount < -20f) {
                            showRail = false
                        }
                    }
                )
            }
    ) {
        WalkmanScaffold(
            playerState = playerState,
            isRootRoute = isRootRoute,
            isPlayerRoute = isPlayerRoute,
            baseRoute = baseRoute,
            onToggleRail = { toggleRail() },
            onOpenSearch = { navController.navigate("search") },
            onTogglePlayPause = playerViewModel::togglePlayPause,
            onNavigateToPlayer = { playerExpanded = true },
            onNext = playerViewModel::next,
            onPrevious = playerViewModel::previous
        ) { innerPadding ->
            WalkmanNavHost(
                navController = navController,
                playerViewModel = playerViewModel,
                libraryViewModel = libraryViewModel,
                settingsViewModel = settingsViewModel,
                libraryTab = libraryTab,
                onLibraryTabSelected = { libraryTab = it },
                modifier = Modifier.padding(innerPadding)
            )
        }

        AnimatedVisibility(
            visible = playerExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            WalkmanScreen(
                viewModel = playerViewModel,
                onOpenQueue = {
                    playerExpanded = false
                    val popped = navController.popBackStack("queue", false)
                    if (!popped) {
                        navController.navigate("queue") { launchSingleTop = true }
                    }
                },
                onLogoClick = { toggleRail() },
                onOpenArtist = { artistId ->
                    playerExpanded = false
                    navController.navigate("artist/$artistId")
                },
                onOpenAlbum = { albumId ->
                    playerExpanded = false
                    navController.navigate("album/$albumId")
                },
                onOpenSearch = {
                    playerExpanded = false
                    navController.navigate("search")
                },
                onCollapse = { playerExpanded = false }
            )
        }

        AnimatedVisibility(
            visible = showRail,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showRail = false }
            )
        }

        WalkmanNavigationRail(
            visible = showRail,
            containerColor = animatedTop,
            accentHighlight = playerState.accentHighlight,
            isHomeRoute = baseRoute == "home",
            selectedIndex = selectedIndex,
            navItems = navItems,
            navController = navController,
            onNavigate = { item ->
                if (item.baseRoute == "library") {
                    libraryTab = item.tabIndex
                }
                playerExpanded = false
                navController.navigate(item.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                showRail = false
            },
            modifier = Modifier.align(Alignment.CenterStart),
            onDismiss = { showRail = false }
        )
    }
}
}

@Composable
private fun WalkmanNavHost(
    navController: NavHostController,
    playerViewModel: PlayerControllerViewModel,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
    libraryTab: Int,
    onLibraryTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
    composable("library") {
        LibraryScreen(
            libraryViewModel = libraryViewModel,
            playerViewModel = playerViewModel,
            selectedTab = libraryTab,
            onTabSelected = onLibraryTabSelected,
            onOpenAlbum = { albumId -> navController.navigate("album/$albumId") },
            onOpenArtist = { artistId -> navController.navigate("artist/$artistId") },
            onOpenFavorites = { navController.navigate("favorites") }
        )
    }
    composable("home") {
        HomeScreen(
            libraryViewModel = libraryViewModel,
            playerViewModel = playerViewModel,
            onOpenAlbum = { albumId -> navController.navigate("album/$albumId") },
            onOpenArtist = { artistId -> navController.navigate("artist/$artistId") }
        )
    }
        composable("queue") {
            QueueScreen(
                playerViewModel = playerViewModel
            )
        }
        composable("settings") {
            SettingsScreen(
                settingsViewModel = settingsViewModel
            )
        }
        composable("album/{albumId}") { entry ->
            AlbumDetailScreen(
                albumId = entry.arguments?.getString("albumId")?.toLongOrNull() ?: 0L,
                libraryViewModel = libraryViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("artist/{artistId}") { entry ->
            ArtistDetailScreen(
                artistId = entry.arguments?.getString("artistId")?.toLongOrNull() ?: 0L,
                libraryViewModel = libraryViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenAlbum = { albumId -> navController.navigate("album/$albumId") }
            )
        }
        composable("favorites") {
            FavoritesScreen(
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("search") {
            SearchScreen(
                libraryViewModel = libraryViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenAlbum = { albumId -> navController.navigate("album/$albumId") }
            )
        }
    }
}

@Composable
private fun WalkmanPermissionHandler(
    onPermissionGranted: () -> Unit,
    context: Context = LocalContext.current
) {
    var hasAudioPermission by remember {
        mutableStateOf(checkAudioPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        hasAudioPermission = audioGranted
        if (!audioGranted) {
            Toast.makeText(
                context,
                context.getString(R.string.permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(requested)
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            onPermissionGranted()
        }
    }
}

private fun checkAudioPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED
}

