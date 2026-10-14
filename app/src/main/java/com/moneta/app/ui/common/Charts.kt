package com.moneta.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.moneta.app.core.money.Money
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MonetaSpacing

/** Parses a category/account "#RRGGBB" hex color, falling back to gray for any malformed value. */
fun parseColorOrGray(colorHex: String): Color = try {
    Color(android.graphics.Color.parseColor(colorHex))
} catch (e: IllegalArgumentException) {
    Color.Gray
}

/** A simple ring donut chart, one arc per slice, colored by each slice's own category color (Section 9). */
@Composable
fun CategoryDonutChart(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    val strokeWidthPx = with(LocalDensity.current) { 12.dp.toPx() }
    Canvas(modifier = modifier) {
        var startAngle = -90f
        val diameter = size.minDimension
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        slices.forEach { slice ->
            val sweep = (slice.percentOfTotal / 100.0 * 360.0).toFloat()
            drawArc(
                color = parseColorOrGray(slice.colorHex),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx)
            )
            startAngle += sweep
        }
    }
}

/** The legend rows to pair with [CategoryDonutChart] - a colored dot, the name, and the amount. */
@Composable
fun CategoryLegendList(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        slices.forEach { slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MonetaSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier
                            .size(10.dp)
                            .background(parseColorOrGray(slice.colorHex), CircleShape)
                    ) {}
                    Spacer(Modifier.width(MonetaSpacing.xs))
                    Text(slice.name, style = MaterialTheme.typography.bodyLarge)
                }
                com.moneta.app.ui.theme.MoneyText(slice.amount)
            }
        }
    }
}

/**
 * A grouped bar chart: two bars (income/expense) per category label, for the
 * Reports "Overview"/"Cash flow" views (Section 17). Deliberately simple -
 * no axis ticks or gridlines - consistent with the design system's "no 3D or
 * exploded charts" rule and kept dependency-free.
 */
@Composable
fun IncomeExpenseBarChart(
    labels: List<String>,
    incomeValues: List<Money>,
    expenseValues: List<Money>,
    modifier: Modifier = Modifier
) {
    val maxValue = (incomeValues + expenseValues).maxOfOrNull { it.minorUnits }?.coerceAtLeast(1) ?: 1
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            labels.indices.forEach { index ->
                val incomeFraction = incomeValues[index].minorUnits.toFloat() / maxValue.toFloat()
                val expenseFraction = expenseValues[index].minorUnits.toFloat() / maxValue.toFloat()
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight(incomeFraction.coerceIn(0.02f, 1f))
                            .background(IncomeGreen)
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight(expenseFraction.coerceIn(0.02f, 1f))
                            .background(ExpenseRed)
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEach { label ->
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A minimal line chart connecting each point in order, for the net-worth trend (Section 17). */
@Composable
fun SimpleLineChart(values: List<Float>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (values.size < 2) return
    val strokeWidthPx = with(LocalDensity.current) { 3.dp.toPx() }
    val minValue = values.min()
    val maxValue = values.max().let { if (it == minValue) it + 1f else it }
    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            val normalized = (value - minValue) / (maxValue - minValue)
            val x = index * stepX
            val y = size.height - (normalized * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidthPx))
    }
}

/** A real filled pie chart (no hole), one wedge per slice in the category's own color. */
@Composable
fun CategoryPieChart(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        var startAngle = -90f
        val diameter = size.minDimension
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        slices.forEach { slice ->
            val sweep = (slice.percentOfTotal / 100.0 * 360.0).toFloat()
            drawArc(
                color = parseColorOrGray(slice.colorHex),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )
            startAngle += sweep
        }
    }
}

/**
 * Income-vs-expense bars over time, with the highest value printed on top and a
 * label under each group. Values are minor units. [labelEvery] thins out the
 * x-axis labels when there are many bars (for example one per day).
 */
@Composable
fun TrendBarChart(
    labels: List<String>,
    incomeMinor: List<Long>,
    expenseMinor: List<Long>,
    currencyCode: String,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1
) {
    val maxValue = (incomeMinor + expenseMinor).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Column(modifier = modifier) {
        Text(
            "Highest bar: " + Money(maxValue, currencyCode).formatted(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            labels.indices.forEach { index ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val inc = incomeMinor[index]
                    val exp = expenseMinor[index]
                    if (inc > 0) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight((inc.toFloat() / maxValue.toFloat()).coerceIn(0.02f, 1f))
                                .background(IncomeGreen)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (exp > 0) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight((exp.toFloat() / maxValue.toFloat()).coerceIn(0.02f, 1f))
                                .background(ExpenseRed)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = if (index % labelEvery.coerceAtLeast(1) == 0) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
