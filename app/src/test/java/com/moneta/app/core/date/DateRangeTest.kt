package com.moneta.app.core.date

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateRangeTest {

    @Test
    fun `currentMonth with default first-day-of-month starts on the 1st`() {
        val reference = LocalDate.of(2026, 3, 15)
        val range = DateRange.currentMonth(firstDayOfMonth = 1, referenceDate = reference)
        assertEquals(LocalDate.of(2026, 3, 1), range.start)
        assertEquals(LocalDate.of(2026, 4, 1), range.endExclusive)
    }

    @Test
    fun `currentMonth respects a custom salary-cycle first day, before it in the prior month`() {
        // If today is March 3rd and the cycle starts on the 5th, we're still in February's cycle.
        val reference = LocalDate.of(2026, 3, 3)
        val range = DateRange.currentMonth(firstDayOfMonth = 5, referenceDate = reference)
        assertEquals(LocalDate.of(2026, 2, 5), range.start)
        assertEquals(LocalDate.of(2026, 3, 5), range.endExclusive)
    }

    @Test
    fun `currentMonth respects a custom salary-cycle first day, on or after it in the current month`() {
        val reference = LocalDate.of(2026, 3, 10)
        val range = DateRange.currentMonth(firstDayOfMonth = 5, referenceDate = reference)
        assertEquals(LocalDate.of(2026, 3, 5), range.start)
        assertEquals(LocalDate.of(2026, 4, 5), range.endExclusive)
    }

    @Test
    fun `previousMonth is exactly one month behind currentMonth`() {
        val reference = LocalDate.of(2026, 3, 15)
        val current = DateRange.currentMonth(referenceDate = reference)
        val previous = DateRange.previousMonth(referenceDate = reference)
        assertEquals(current.start, previous.endExclusive)
        assertEquals(LocalDate.of(2026, 2, 1), previous.start)
    }

    @Test
    fun `range end is exclusive - the first day of the next period is not contained`() {
        val range = DateRange.currentMonth(referenceDate = LocalDate.of(2026, 3, 15))
        assertTrue(LocalDate.of(2026, 3, 31) in range)
        assertFalse(LocalDate.of(2026, 4, 1) in range)
    }

    @Test
    fun `custom range is inclusive of both the given start and end dates`() {
        val range = DateRange.custom(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        assertTrue(LocalDate.of(2026, 1, 1) in range)
        assertTrue(LocalDate.of(2026, 1, 31) in range)
        assertFalse(LocalDate.of(2026, 2, 1) in range)
    }

    @Test
    fun `currentYear spans January 1st through the end of December`() {
        val range = DateRange.currentYear(referenceDate = LocalDate.of(2026, 6, 1))
        assertEquals(LocalDate.of(2026, 1, 1), range.start)
        assertEquals(LocalDate.of(2027, 1, 1), range.endExclusive)
    }
}
