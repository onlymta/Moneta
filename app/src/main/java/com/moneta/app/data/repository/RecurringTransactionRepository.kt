package com.moneta.app.data.repository

import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.RecurringTransactionDao
import com.moneta.app.data.local.entity.RecurrenceFrequency
import com.moneta.app.data.local.entity.RecurringTransactionEntity
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.domain.usecase.RecurrenceCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Safety cap on how many occurrences a single generation pass will create for one rule. */
private const val MAX_OCCURRENCES_PER_PASS = 366

@Singleton
class RecurringTransactionRepository @Inject constructor(
    private val recurringDao: RecurringTransactionDao,
    private val transactionRepository: TransactionRepository,
    private val recurrenceCalculator: RecurrenceCalculator
) {

    fun observeActive(): Flow<List<RecurringTransaction>> =
        recurringDao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeAll(): Flow<List<RecurringTransaction>> =
        recurringDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): RecurringTransaction? = recurringDao.getById(id)?.toDomain()

    suspend fun create(
        templateType: TransactionType,
        amount: Money,
        accountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        frequency: RecurrenceFrequency,
        intervalCount: Int,
        startDate: LocalDate,
        endDate: LocalDate?,
        reminderEnabled: Boolean,
        reminderOffsetDays: Int,
        notes: String?,
        payee: String?
    ): Long = recurringDao.insert(
        RecurringTransactionEntity(
            templateType = templateType,
            amountMinor = amount.minorUnits,
            currencyCode = amount.currencyCode,
            accountId = accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
            frequency = frequency,
            intervalCount = intervalCount,
            startDate = startDate.toEpochDay(),
            endDate = endDate?.toEpochDay(),
            nextOccurrenceDate = startDate.toEpochDay(),
            reminderEnabled = reminderEnabled,
            reminderOffsetDays = reminderOffsetDays,
            notes = notes,
            payee = payee
        )
    )

    suspend fun setActive(id: Long, isActive: Boolean) = recurringDao.setActive(id, isActive)

    suspend fun delete(id: Long) = recurringDao.delete(id)

    /**
     * The idempotent generation pass (Section 15). For every active rule
     * whose [RecurringTransactionEntity.nextOccurrenceDate] is today or
     * earlier, this creates real [Transaction] rows through
     * TransactionRepository's normal atomic write path - never a shortcut
     * that writes a row without updating balances - and advances
     * nextOccurrenceDate past each one it creates, one at a time, so a crash
     * partway through leaves already-generated occurrences correctly
     * recorded rather than silently lost or duplicated on the next run.
     *
     * Capped at [MAX_OCCURRENCES_PER_PASS] per rule so a rule left inactive
     * for years (or a bad end date) can't generate an unbounded backlog in
     * one pass.
     */
    suspend fun generateDueOccurrences(today: LocalDate = DateRange.todayDate()): Int {
        val due = recurringDao.getDue(today.toEpochDay())
        var generatedCount = 0

        due.forEach { rule ->
            var occurrenceDate = LocalDate.ofEpochDay(rule.nextOccurrenceDate)
            var iterations = 0

            while (!occurrenceDate.isAfter(today) &&
                (rule.endDate == null || occurrenceDate.toEpochDay() <= rule.endDate) &&
                iterations < MAX_OCCURRENCES_PER_PASS
            ) {
                val alreadyExists = recurringDao.occurrenceAlreadyExists(rule.id, occurrenceDate.toEpochDay()) > 0
                if (!alreadyExists) {
                    transactionRepository.record(
                        TransactionDraft(
                            type = rule.templateType,
                            amount = Money(rule.amountMinor, rule.currencyCode),
                            accountId = rule.accountId,
                            toAccountId = rule.toAccountId,
                            categoryId = rule.categoryId,
                            date = occurrenceDate,
                            timeMinutesSinceMidnight = 0,
                            payee = rule.payee,
                            notes = rule.notes
                        ),
                    ).also { generatedCount++ }
                }
                occurrenceDate = recurrenceCalculator.nextDate(occurrenceDate, rule.frequency, rule.intervalCount)
                recurringDao.advanceNextOccurrence(rule.id, occurrenceDate.toEpochDay())
                iterations++

                val pastEndDate = rule.endDate != null && occurrenceDate.toEpochDay() > rule.endDate
                if (pastEndDate) {
                    recurringDao.setActive(rule.id, false)
                    break
                }
            }
        }
        return generatedCount
    }

    private fun RecurringTransactionEntity.toDomain() = RecurringTransaction(
        id = id,
        templateType = templateType,
        amount = Money(amountMinor, currencyCode),
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        frequency = frequency,
        intervalCount = intervalCount,
        startDate = LocalDate.ofEpochDay(startDate),
        endDate = endDate?.let { LocalDate.ofEpochDay(it) },
        nextOccurrenceDate = LocalDate.ofEpochDay(nextOccurrenceDate),
        reminderEnabled = reminderEnabled,
        reminderOffsetDays = reminderOffsetDays,
        isActive = isActive,
        notes = notes,
        payee = payee
    )
}
