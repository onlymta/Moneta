package com.moneta.app.domain.insights

import com.moneta.app.core.money.Money
import com.moneta.app.data.repository.BudgetProgress
import com.moneta.app.data.repository.Transaction
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.domain.usecase.MonthlyCashFlow

/**
 * Everything an [InsightRule] might need, gathered once by InsightsViewModel
 * so rules stay pure functions over data rather than each making their own
 * repository calls (Section 46: no duplicate business logic, and it keeps
 * rules trivially unit-testable). Adding a new rule should never require a
 * new repository query unless the data genuinely isn't here yet.
 */
data class InsightContext(
    val currencyCode: String,
    val currentMonthExpenseByCategory: List<CategorySlice>,
    val previousMonthExpenseByCategory: List<CategorySlice>,
    /** Oldest first. At least 2 entries are needed for any month-over-month rule to fire. */
    val monthlyCashFlow: List<MonthlyCashFlow>,
    val budgetProgress: List<BudgetProgress>,
    val currentMonthExpenseTransactions: List<Transaction>
)

enum class InsightSeverity { INFO, NOTICE, WARNING }

data class Insight(
    val id: String,
    val severity: InsightSeverity,
    val title: String,
    val body: String
)

/**
 * One rule-based observation (Section 20). A rule MUST return null rather
 * than a low-confidence guess when [InsightContext] doesn't have enough data
 * for it to say something true (Section 19: "if there is insufficient data,
 * clearly say that more data is required" - handled by the screen showing an
 * empty state when the whole list comes back empty, rather than each rule
 * inventing a placeholder sentence).
 */
fun interface InsightRule {
    fun evaluate(context: InsightContext): Insight?
}
