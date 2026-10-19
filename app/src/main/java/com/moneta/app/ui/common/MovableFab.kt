package com.moneta.app.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.moneta.app.ui.theme.AccentAmber
import kotlin.math.roundToInt

/**
 * A round "+" button that can be dragged out of the way when it covers something.
 * It starts in its normal corner and goes back there whenever [resetKey] changes
 * (the app passes the current page) or the screen it belongs to is left.
 *
 * Quick Add uses [FabStyle.TRANSACTION] (teal); add-something-else buttons use
 * [FabStyle.OTHER] (amber) so the two are easy to tell apart.
 */
enum class FabStyle { TRANSACTION, OTHER }

@Composable
fun MovableFab(
    onClick: () -> Unit,
    contentDescription: String,
    style: FabStyle,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    icon: ImageVector = Icons.Filled.Add
) {
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val maxLeft = with(density) { -(config.screenWidthDp.dp - 96.dp).toPx() }
    val maxUp = with(density) { -(config.screenHeightDp.dp - 260.dp).toPx() }
    val maxRight = with(density) { 16.dp.toPx() }
    val maxDown = with(density) { 40.dp.toPx() }

    val container = if (style == FabStyle.TRANSACTION) androidx.compose.material3.MaterialTheme.colorScheme.primary else AccentAmber
    val content = if (style == FabStyle.TRANSACTION) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary else Color(0xFF2B1D00)

    FloatingActionButton(
        onClick = onClick,
        containerColor = container,
        contentColor = content,
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset = Offset(
                        (offset.x + dragAmount.x).coerceIn(maxLeft, maxRight),
                        (offset.y + dragAmount.y).coerceIn(maxUp, maxDown)
                    )
                }
            }
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
