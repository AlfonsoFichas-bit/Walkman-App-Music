package com.buga.walkman.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance

val LocalPlayerCoverAccent = staticCompositionLocalOf { Color.Unspecified }

private val LocalExpressiveItemColors = staticCompositionLocalOf<MenuItemColors?> { null }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveItemsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    darkOverlay: Color = Color(0x99000000),
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveAccent = (accentColor ?: LocalPlayerCoverAccent.current.takeIf { it != Color.Unspecified })
        ?.let { color -> compositeOver(darkOverlay, color) }

    val onColor = effectiveAccent?.let { color ->
        if (color.luminance() > 0.5f) Color.Black else Color.White
    }
    val itemColors = if (effectiveAccent != null && onColor != null) {
        MenuItemColors(
            textColor = onColor,
            leadingIconColor = onColor,
            trailingIconColor = onColor,
            disabledTextColor = onColor.copy(alpha = 0.38f),
            disabledLeadingIconColor = onColor.copy(alpha = 0.38f),
            disabledTrailingIconColor = onColor.copy(alpha = 0.38f),
            containerColor = effectiveAccent,
            disabledContainerColor = effectiveAccent.copy(alpha = 0.38f),
        )
    } else {
        MenuDefaults.itemColors()
    }

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, 1),
            containerColor = effectiveAccent ?: MenuDefaults.groupStandardContainerColor
        ) {
            CompositionLocalProvider(LocalExpressiveItemColors provides itemColors) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveMenuItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    DropdownMenuItem(
        onClick = onClick,
        text = text,
        shape = expressiveItemShape(index, count),
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingContent = trailingIcon,
        colors = LocalExpressiveItemColors.current ?: MenuDefaults.itemColors()
    )
}

@Composable
internal fun expressiveItemShape(index: Int, count: Int): Shape = when {
    count == 1 -> MenuDefaults.standaloneItemShape
    index == 0 -> MenuDefaults.leadingItemShape
    index == count - 1 -> MenuDefaults.trailingItemShape
    else -> MenuDefaults.middleItemShape
}

fun compositeOver(top: Color, bottom: Color): Color {
    val alpha = top.alpha + bottom.alpha * (1f - top.alpha)
    if (alpha == 0f) return Color.Transparent
    return Color(
        red = (top.red * top.alpha + bottom.red * bottom.alpha * (1f - top.alpha)) / alpha,
        green = (top.green * top.alpha + bottom.green * bottom.alpha * (1f - top.alpha)) / alpha,
        blue = (top.blue * top.alpha + bottom.blue * bottom.alpha * (1f - top.alpha)) / alpha,
        alpha = alpha
    )
}