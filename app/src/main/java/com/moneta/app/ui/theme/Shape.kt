package com.moneta.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner-radius scale from ARCHITECTURE.md Section 9 - never mixed within one component family. */
object MonetaRadius {
    val small = 12.dp   // chips, inputs
    val medium = 16.dp  // cards
    val large = 28.dp   // sheets, dialogs, FAB
}

val MonetaShapes = Shapes(
    small = RoundedCornerShape(MonetaRadius.small),
    medium = RoundedCornerShape(MonetaRadius.medium),
    large = RoundedCornerShape(MonetaRadius.large)
)

/** Spacing scale (4dp base unit) from Section 9 - use these instead of ad-hoc dp values. */
object MonetaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Fixed screen horizontal margin per the design system. */
    val screenHorizontal = lg
    /** Fixed card internal padding per the design system. */
    val cardPadding = lg
}
