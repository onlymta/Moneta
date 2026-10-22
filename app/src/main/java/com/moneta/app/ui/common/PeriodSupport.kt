@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.moneta.app.core.date.DateRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** How wide a slice of time a screen is showing. */
enum class PeriodMode(val label: String) {
    DAY("Daily"),
    WEEK("Weekly"),
    MONTH("Monthly"),
    YEAR("Yearly"),
    ALL("All time"),
    CUSTOM("Custom range")
}

/**
 * One chosen period (a day, a week, a month, a year, everything, or a custom
 * from-to range). Screens keep one of these in their ViewModel and read
 * [range] to query, so "which dates is this?" is decided in exactly one place.
 */
data class PeriodSelection(
    val mode: PeriodMode = PeriodMode.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val customStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val customEnd: LocalDate = LocalDate.now()
) {
    fun range(): DateRange = when (mode) {
        PeriodMode.DAY -> DateRange(anchor, anchor.plusDays(1))
        PeriodMode.WEEK -> {
            val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DateRange(start, start.plusWeeks(1))
        }
        PeriodMode.MONTH -> {
            val start = anchor.withDayOfMonth(1)
            DateRange(start, start.plusMonths(1))
        }
        PeriodMode.YEAR -> {
            val start = anchor.withDayOfYear(1)
            DateRange(start, start.plusYears(1))
        }
        PeriodMode.ALL -> DateRange(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1))
        PeriodMode.CUSTOM -> DateRange(customStart, customEnd.plusDays(1))
    }

    val canShift: Boolean get() = mode != PeriodMode.ALL && mode != PeriodMode.CUSTOM

    fun shift(delta: Int): PeriodSelection = when (mode) {
        PeriodMode.DAY -> copy(anchor = anchor.plusDays(delta.toLong()))
        PeriodMode.WEEK -> copy(anchor = anchor.plusWeeks(delta.toLong()))
        PeriodMode.MONTH -> copy(anchor = anchor.withDayOfMonth(1).plusMonths(delta.toLong()))
        PeriodMode.YEAR -> copy(anchor = anchor.withDayOfYear(1).plusYears(delta.toLong()))
        PeriodMode.ALL, PeriodMode.CUSTOM -> this
    }

    /** True when this selection contains today (used to decide things like "this month" wording). */
    fun containsToday(): Boolean = LocalDate.now() in range()

    /** A short tag for chart axes: "Oct 26", "6 Oct", "2026". */
    fun shortLabel(): String = when (mode) {
        PeriodMode.DAY, PeriodMode.WEEK -> range().start.format(DateTimeFormatter.ofPattern("d MMM"))
        PeriodMode.MONTH -> anchor.format(DateTimeFormatter.ofPattern("MMM yy"))
        PeriodMode.YEAR -> anchor.format(DateTimeFormatter.ofPattern("yyyy"))
        PeriodMode.ALL -> "All"
        PeriodMode.CUSTOM -> "Range"
    }

    fun label(): String {
        val r = range()
        val last = r.endExclusive.minusDays(1)
        return when (mode) {
            PeriodMode.DAY -> anchor.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
            PeriodMode.WEEK -> r.start.format(DateTimeFormatter.ofPattern("d MMM")) + " - " +
                last.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            PeriodMode.MONTH -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            PeriodMode.YEAR -> anchor.format(DateTimeFormatter.ofPattern("yyyy"))
            PeriodMode.ALL -> "All time"
            PeriodMode.CUSTOM -> customStart.format(DateTimeFormatter.ofPattern("d MMM yyyy")) + " - " +
                customEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
    }
}

private fun millisOf(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * The shared "< October 2026 >" bar. Tap the arrows to step back/forward; tap the
 * label to switch between daily/weekly/monthly/yearly/all-time/custom (only the
 * modes in [modes] are offered - with a single mode the label isn't tappable).
 * With [calendarPick] the label opens a calendar instead, and the period type is chosen
 * elsewhere with a [PeriodModeButton].
 */
@Composable
fun PeriodBar(
    selection: PeriodSelection,
    onChange: (PeriodSelection) -> Unit,
    modifier: Modifier = Modifier,
    modes: List<PeriodMode> = PeriodMode.entries,
    calendarPick: Boolean = false
) {
    var menuOpen by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onChange(selection.shift(-1)) },
            enabled = selection.canShift,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous")
        }
        Box {
            val showNow = selection.mode != PeriodMode.ALL && selection.mode != PeriodMode.CUSTOM
            val isNow = showNow && selection.containsToday()
            val isPast = showNow && !isNow && selection.range().endExclusive.isBefore(LocalDate.now().plusDays(1))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isNow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent,
                modifier = Modifier.clickable(enabled = modes.size > 1 || calendarPick) {
                    if (calendarPick && selection.canShift) calendarOpen = true else menuOpen = true
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            selection.label(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                            color = if (isNow) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (showNow) {
                            Text(
                                if (isNow) "\u25CF Current" else if (isPast) "Past" else "Upcoming",
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                color = if (isNow) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    if (modes.size > 1 && !calendarPick) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change period type", modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (!calendarPick) {
                PeriodModeMenu(selection, onChange, modes, menuOpen) { menuOpen = false }
            }
        }
        IconButton(
            onClick = { onChange(selection.shift(1)) },
            enabled = selection.canShift,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next")
        }
    }

    if (calendarOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = millisOf(selection.anchor))
        DatePickerDialog(
            onDismissRequest = { calendarOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(selection.copy(anchor = dateOf(it))) }
                    calendarOpen = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { calendarOpen = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state, title = { Text("  Jump to a date") })
        }
    }
}

/** An icon button that opens the day / week / month / year / all time / custom menu. Sized like its neighbours. */
@Composable
fun PeriodModeButton(
    selection: PeriodSelection,
    onChange: (PeriodSelection) -> Unit,
    modifier: Modifier = Modifier,
    modes: List<PeriodMode> = PeriodMode.entries
) {
    var menuShown by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { menuShown = true }) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = "Show records by " + selection.mode.label.lowercase(),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        PeriodModeMenu(selection, onChange, modes, menuShown) { menuShown = false }
    }
}

/** The list of period types plus the two date pickers a custom range needs. */
@Composable
private fun PeriodModeMenu(
    selection: PeriodSelection,
    onChange: (PeriodSelection) -> Unit,
    modes: List<PeriodMode>,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(selection.customStart) }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        modes.forEach { mode ->
            DropdownMenuItem(
                text = {
                    Text(
                        (if (mode == selection.mode) "\u2713  " else "     ") + mode.label,
                        fontWeight = if (mode == selection.mode) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onDismiss()
                    if (mode == PeriodMode.CUSTOM) {
                        pickStart = true
                    } else {
                        // Keep the day you were looking at when switching between day/week/month/year.
                        val anchor = if (selection.mode == PeriodMode.CUSTOM) selection.customEnd else selection.anchor
                        onChange(selection.copy(mode = mode, anchor = anchor))
                    }
                }
            )
        }
    }

    if (pickStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = millisOf(selection.customStart))
        DatePickerDialog(
            onDismissRequest = { pickStart = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingStart = state.selectedDateMillis?.let { dateOf(it) } ?: selection.customStart
                    pickStart = false
                    pickEnd = true
                }) { Text("Next: end date") }
            },
            dismissButton = { TextButton(onClick = { pickStart = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state, title = { Text("  Start date") })
        }
    }

    if (pickEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = millisOf(selection.customEnd))
        DatePickerDialog(
            onDismissRequest = { pickEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    val end = state.selectedDateMillis?.let { dateOf(it) } ?: selection.customEnd
                    val from = if (end.isBefore(pendingStart)) end else pendingStart
                    val to = if (end.isBefore(pendingStart)) pendingStart else end
                    onChange(selection.copy(mode = PeriodMode.CUSTOM, customStart = from, customEnd = to))
                    pickEnd = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pickEnd = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state, title = { Text("  End date") })
        }
    }
}
