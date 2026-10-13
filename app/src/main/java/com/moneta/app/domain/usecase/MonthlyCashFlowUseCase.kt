package com.moneta.app.domain.usecase

import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

data class MonthlyCashFlow(
    val monthStart: LocalDate,
    val income: Money,
    val expense: Money
) {
    val net: Money get() = income - expense
}

/**
 * Computes income/expense/net for each of the last [monthsBack] calendar
 * months (most recent last). Used by both the Reports "Income vs Expense"
 * and "Cash flow" views so they can never show different numbers for the
 * same month (Section 17).
 */
class MonthlyCashFlowUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(monthsBack: Int, currencyCode: String): List<MonthlyCashFlow> {
        val today = DateRange.todayDate()
        return (monthsBack - 1 downTo 0).map { offset ->
            val referenceDate = today.minusMonths(offset.toLong())
            val range = DateRange.currentMonth(referenceDate = referenceDate)
            val income = transactionRepository
                .observeTotalForTypeInRange(TransactionType.INCOME, range, currencyCode)
                .first()
            val expense = transactionRepository
                .observeTotalForTypeInRange(TransactionType.EXPENSE, range, currencyCode)
                .first()
            MonthlyCashFlow(monthStart = range.start, income = income, expense = expense)
        }
    }
}
