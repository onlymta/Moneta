package com.moneta.app.domain.usecase

import com.moneta.app.data.local.entity.RecurrenceFrequency
import java.time.LocalDate
import javax.inject.Inject

/**
 * Computes the next occurrence date for a recurring transaction rule. Pure
 * function, no repository/database access - this is deliberately its own
 * tiny class so RecurringTransactionRepository's idempotent-generation logic
 * (Section 15) can be tested against this in isolation from Room.
 */
class RecurrenceCalculator @Inject constructor() {
    fun nextDate(current: LocalDate, frequency: RecurrenceFrequency, intervalCount: Int): LocalDate {
        require(intervalCount >= 1) { "intervalCount must be at least 1" }
        return when (frequency) {
            RecurrenceFrequency.DAILY -> current.plusDays(intervalCount.toLong())
            RecurrenceFrequency.WEEKLY -> current.plusWeeks(intervalCount.toLong())
            RecurrenceFrequency.MONTHLY -> current.plusMonths(intervalCount.toLong())
            RecurrenceFrequency.YEARLY -> current.plusYears(intervalCount.toLong())
        }
    }
}
