package com.moneta.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A category/account/goal's icon on a soft-tinted circular background using
 * its own stored color (Section 3: "the one place saturated color is
 * allowed"). Used everywhere a category or account needs to show its icon,
 * so every screen renders it identically.
 */
@Composable
fun IconBadge(iconName: String, colorHex: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = parseColorOrGray(colorHex)
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = IconMapper.iconFor(iconName),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
