package com.buga.walkman.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.buga.walkman.R

internal data class NavItem(
    val baseRoute: String,
    val tabIndex: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
) {
    val route: String
        get() = baseRoute
}

@Composable
internal fun WalkmanNavigationRail(
    visible: Boolean,
    containerColor: Color,
    accentHighlight: Color,
    isHomeRoute: Boolean,
    selectedIndex: Int,
    navItems: List<NavItem>,
    navController: NavHostController,
    onNavigate: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(),
        exit = shrinkHorizontally(),
        modifier = modifier
    ) {
        NavigationRail(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding(),
            containerColor = containerColor,
            contentColor = Color.White
        ) {
            NavigationRailItem(
                icon = {
                    Icon(
                        if (isHomeRoute) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = stringResource(R.string.screen_home)
                    )
                },
                selected = isHomeRoute,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.White,
                    unselectedIconColor = Color.White.copy(alpha = 0.7f),
                    indicatorColor = accentHighlight.copy(alpha = 0.3f)
                ),
                onClick = {
                    navController.navigate("home") { launchSingleTop = true }
                    onDismiss()
                }
            )

            navItems.forEachIndexed { index, item ->
                NavigationRailItem(
                    icon = {
                        Icon(
                            if (selectedIndex == index) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    },
                    selected = selectedIndex == index,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.7f),
                        indicatorColor = accentHighlight.copy(alpha = 0.3f)
                    ),
                    onClick = {
                        onNavigate(item)
                    }
                )
            }
        }
    }
}
