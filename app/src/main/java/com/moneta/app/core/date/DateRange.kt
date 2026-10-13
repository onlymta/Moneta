package com.moneta.app.core.date

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * A closed-open local-calendar date range: [start, end).
 *
 * ARCHITECTURE.md's technical-risk table calls out timezone/date-boundary bugs
 * as a named risk. To avoid every screen recomputing "what counts as this
 * month" slightly differently, every report/budget/dashboard period MUST be
 * built through the factory functions below rather than hand-rolled date math.
 */
data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {

    operator fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && date.isBefore(endExclusive)

    companion object {

        /** The device's local zone, used consistently for "what day is it" everywhere. */
        fun deviceZone(): ZoneId = ZoneId.systemDefault()

        /** Today's date in the device's local zone - the single place "what day is it" is computed. */
        fun todayDate(zone: ZoneId = deviceZone()): LocalDate = LocalDate.now(zone)

        /**
         * The current calendar month as a range, respecting a user-configurable
         * "first day of month" setting (Section 37) - most users want the 1st,
         * but some track finances against a salary-cycle start day instead.
         */
        fun currentMonth(firstDayOfMonth: Int = 1, referenceDate: LocalDate = todayDate()): DateRange {
            val anchor = referenceDate.withDayOfMonth(
                firstDayOfMonth.coerceIn(1, referenceDate.lengthOfMonth())
            )
            val start = if (referenceDate.dayOfMonth < firstDayOfMonth) {
                anchor.minusMonths(1)
            } else anchor
            return DateRange(start, start.plusMonths(1))
        }

        fun previousMonth(firstDayOfMonth: Int = 1, referenceDate: LocalDate = todayDate()): DateRange {
            val current = currentMonth(firstDayOfMonth, referenceDate)
            return DateRange(current.start.minusMonths(1), current.start)
        }

        fun currentWeek(referenceDate: LocalDate = todayDate()): DateRange {
            val start = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return DateRange(start, start.plusWeeks(1))
        }

        fun currentYear(referenceDate: LocalDate = todayDate()): DateRange {
            val start = referenceDate.withDayOfYear(1)
            return DateRange(start, start.plusYears(1))
        }

        fun today(referenceDate: LocalDate = todayDate()): DateRange =
            DateRange(referenceDate, referenceDate.plusDays(1))

        fun custom(start: LocalDate, endInclusive: LocalDate): DateRange =
            DateRange(start, endInclusive.plusDays(1))
    }
}
