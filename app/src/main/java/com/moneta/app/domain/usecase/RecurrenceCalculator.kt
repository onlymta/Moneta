package com.moneta.app.domain.usecase

import com.moneta.app.data.local.entity.RecurrenceFrequency
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Computes the next occurrence date for a recurring transaction rule. Pure
 * function, no repository/database access - this is deliberately its own
 * tiny class so RecurringTransactionRepository's idempotent-generation logic
 * (Section 15) can be tested against this in isolation from Room.
 *
 * Monthly rules follow a fixed day of the month ([anchorDay]; 31 means the last day, and a short month uses its
 * last day), so a rule on the 31st does not drift to the 28th after February.
 */
class RecurrenceCalculator @Inject constructor() {
    fun nextDate(current: LocalDate, frequency: RecurrenceFrequency, intervalCount: Int, anchorDay: Int = 0): LocalDate {
        require(intervalCount >= 1) { "intervalCount must be at least 1" }
        return when (frequency) {
            RecurrenceFrequency.DAILY -> current.plusDays(intervalCount.toLong())
            RecurrenceFrequency.WEEKLY -> current.plusWeeks(intervalCount.toLong())
            RecurrenceFrequency.WEEKDAYS -> {
                var day = current.plusDays(1)
                while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) day = day.plusDays(1)
                day
            }
            RecurrenceFrequency.WEEKENDS -> {
                var day = current.plusDays(1)
                while (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) day = day.plusDays(1)
                day
            }
            RecurrenceFrequency.MONTHLY -> {
                val target = current.withDayOfMonth(1).plusMonths(intervalCount.toLong())
                val wanted = if (anchorDay in 1..31) anchorDay else current.dayOfMonth
                target.withDayOfMonth(minOf(wanted, target.lengthOfMonth()))
            }
            RecurrenceFrequency.YEARLY -> current.plusYears(intervalCount.toLong())
        }
    }

    /** The first date on or after [from] that fits the rule, used when a rule is created or its schedule is changed. */
    fun firstDate(
        from: LocalDate,
        frequency: RecurrenceFrequency,
        weekday: DayOfWeek,
        monthDay: Int,
        yearlyMonth: Int,
        yearlyDay: Int
    ): LocalDate = when (frequency) {
        RecurrenceFrequency.DAILY -> from
        RecurrenceFrequency.WEEKDAYS ->
            if (from.dayOfWeek == DayOfWeek.SATURDAY || from.dayOfWeek == DayOfWeek.SUNDAY) {
                from.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
            } else from
        RecurrenceFrequency.WEEKENDS ->
            if (from.dayOfWeek == DayOfWeek.SATURDAY || from.dayOfWeek == DayOfWeek.SUNDAY) from
            else from.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY))
        RecurrenceFrequency.WEEKLY -> from.with(java.time.temporal.TemporalAdjusters.nextOrSame(weekday))
        RecurrenceFrequency.MONTHLY -> {
            val thisMonth = from.withDayOfMonth(minOf(monthDay, from.lengthOfMonth()))
            if (!thisMonth.isBefore(from)) {
                thisMonth
            } else {
                val next = from.withDayOfMonth(1).plusMonths(1)
                next.withDayOfMonth(minOf(monthDay, next.lengthOfMonth()))
            }
        }
        RecurrenceFrequency.YEARLY -> {
            fun inYear(year: Int): LocalDate {
                val firstOfMonth = LocalDate.of(year, yearlyMonth, 1)
                return firstOfMonth.withDayOfMonth(minOf(yearlyDay, firstOfMonth.lengthOfMonth()))
            }
            val thisYear = inYear(from.year)
            if (!thisYear.isBefore(from)) thisYear else inYear(from.year + 1)
        }
    }
}
