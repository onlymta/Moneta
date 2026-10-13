package com.moneta.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.moneta.app.core.money.Money

/**
 * Renders a [Money] amount with tabular (fixed-width) numerals so columns of
 * amounts line up (ARCHITECTURE.md Section 3/9) - this is a deliberately
 * small, single-purpose composable so every screen that shows an amount goes
 * through the same formatting and figure-alignment rules instead of each
 * screen calling Money.formatted() ad hoc with inconsistent styling.
 */
@Composable
fun MoneyText(
    amount: Money,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    val style = LocalTextStyle.current.copy(
        fontFeatureSettings = TabularFiguresFontFeature
    )
    Text(
        text = amount.formatted(),
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
