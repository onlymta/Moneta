package com.moneta.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One bar: its size, colour and the short text printed above it. */
data class BarValue(val value: Long, val color: Color, val label: String = compactAmount(value))

/** One period on the chart: one or two bars side by side, an axis label and up to a few lines of notes below. */
data class BarGroup(
    val label: String,
    val bars: List<BarValue>,
    val notes: List<String> = emptyList(),
    val highlight: Boolean = false
)

/** 1250000 (minor units) -> "12.5k". Amounts under a thousand are printed whole. */
fun compactAmount(minor: Long): String {
    val major = kotlin.math.abs(minor) / 100.0
    val text = when {
        major >= 1_000_000.0 -> trimZero("%.1f".format(major / 1_000_000.0)) + "M"
        major >= 1_000.0 -> trimZero("%.1f".format(major / 1_000.0)) + "k"
        else -> "%.0f".format(major)
    }
    return if (minor < 0) "-$text" else text
}

private fun trimZero(text: String): String = if (text.endsWith(".0")) text.dropLast(2) else text

/** A small coloured dot with a caption, used above a chart. */
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (text, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(9.dp).background(color, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Side-by-side bars for a handful of periods (oldest first). Every bar shares one scale, the amount sits on
 * top of each bar, and the period label and notes sit underneath. The last group is emphasised.
 */
@Composable
fun GroupedBarChart(groups: List<BarGroup>, modifier: Modifier = Modifier, chartHeight: Dp = 150.dp) {
    val maxValue = (groups.flatMap { it.bars }.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1L)
    val barArea = chartHeight - 16.dp
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { group ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
                ) {
                    group.bars.forEach { bar ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                bar.label,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                fontWeight = if (group.highlight) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            val fraction = if (bar.value <= 0L) 0f else (bar.value.toFloat() / maxValue.toFloat()).coerceIn(0.03f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (bar.value <= 0L) 2.dp else barArea * fraction)
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                    .background(if (group.highlight) bar.color else bar.color.copy(alpha = 0.55f))
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    group.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (group.highlight) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    color = if (group.highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                group.notes.forEach { note ->
                    Text(
                        note,
                        fontSize = 9.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
