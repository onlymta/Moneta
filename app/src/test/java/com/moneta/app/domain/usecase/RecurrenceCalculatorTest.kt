package com.moneta.app.domain.usecase

import com.moneta.app.data.local.entity.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class RecurrenceCalculatorTest {

    private val calculator = RecurrenceCalculator()

    @Test
    fun `daily advances by the interval count`() {
        val result = calculator.nextDate(LocalDate.of(2026, 1, 1), RecurrenceFrequency.DAILY, intervalCount = 3)
        assertEquals(LocalDate.of(2026, 1, 4), result)
    }

    @Test
    fun `weekly advances by whole weeks`() {
        val result = calculator.nextDate(LocalDate.of(2026, 1, 1), RecurrenceFrequency.WEEKLY, intervalCount = 2)
        assertEquals(LocalDate.of(2026, 1, 15), result)
    }

    @Test
    fun `monthly handles end-of-month rollover correctly`() {
        // Jan 31 + 1 month should land on Feb 28 (2026 is not a leap year), not overflow into March.
        val result = calculator.nextDate(LocalDate.of(2026, 1, 31), RecurrenceFrequency.MONTHLY, intervalCount = 1)
        assertEquals(LocalDate.of(2026, 2, 28), result)
    }

    @Test
    fun `yearly advances by whole years`() {
        val result = calculator.nextDate(LocalDate.of(2026, 3, 15), RecurrenceFrequency.YEARLY, intervalCount = 1)
        assertEquals(LocalDate.of(2027, 3, 15), result)
    }

    @Test
    fun `rejects an interval count below 1 rather than looping forever`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.nextDate(LocalDate.of(2026, 1, 1), RecurrenceFrequency.DAILY, intervalCount = 0)
        }
    }

    @Test
    fun `repeated application never goes backwards`() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(12) {
            val next = calculator.nextDate(date, RecurrenceFrequency.MONTHLY, intervalCount = 1)
            assert(next.isAfter(date)) { "Expected $next to be after $date" }
            date = next
        }
    }
}
