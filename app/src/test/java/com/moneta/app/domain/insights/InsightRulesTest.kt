package com.moneta.app.domain.insights

import com.moneta.app.core.money.Money
import com.moneta.app.domain.usecase.MonthlyCashFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

private const val CCY = "PKR"

private fun money(rupees: Long) = Money(rupees * 100, CCY)

private fun emptyContext() = InsightContext(
    currencyCode = CCY,
    currentMonthExpenseByCategory = emptyList(),
    previousMonthExpenseByCategory = emptyList(),
    monthlyCashFlow = emptyList(),
    budgetProgress = emptyList(),
    currentMonthExpenseTransactions = emptyList()
)

class InsightRulesTest {

    @Test
    fun `ConsecutiveIncreaseRule requires at least 3 months of history`() {
        val context = emptyContext().copy(
            monthlyCashFlow = listOf(
                MonthlyCashFlow(LocalDate.of(2026, 1, 1), money(1000), money(500)),
                MonthlyCashFlow(LocalDate.of(2026, 2, 1), money(1000), money(600))
            )
        )
        assertNull("Only 2 months of data - should not claim a 3-month trend", ConsecutiveIncreaseRule.evaluate(context))
    }

    @Test
    fun `ConsecutiveIncreaseRule fires when expense strictly rises for 3 straight months`() {
        val context = emptyContext().copy(
            monthlyCashFlow = listOf(
                MonthlyCashFlow(LocalDate.of(2026, 1, 1), money(1000), money(400)),
                MonthlyCashFlow(LocalDate.of(2026, 2, 1), money(1000), money(500)),
                MonthlyCashFlow(LocalDate.of(2026, 3, 1), money(1000), money(600))
            )
        )
        assertNotNull(ConsecutiveIncreaseRule.evaluate(context))
    }

    @Test
    fun `ConsecutiveIncreaseRule does not fire if any month dips`() {
        val context = emptyContext().copy(
            monthlyCashFlow = listOf(
                MonthlyCashFlow(LocalDate.of(2026, 1, 1), money(1000), money(600)),
                MonthlyCashFlow(LocalDate.of(2026, 2, 1), money(1000), money(500)), // dip
                MonthlyCashFlow(LocalDate.of(2026, 3, 1), money(1000), money(700))
            )
        )
        assertNull(ConsecutiveIncreaseRule.evaluate(context))
    }

    @Test
    fun `BudgetRiskRule ignores budgets well under their threshold`() {
        val context = emptyContext().copy(
            budgetProgress = listOf(fakeBudgetProgress(percentUsed = 40.0))
        )
        assertNull(BudgetRiskRule.evaluate(context))
    }

    @Test
    fun `BudgetRiskRule fires once a budget crosses 70 percent`() {
        val context = emptyContext().copy(
            budgetProgress = listOf(fakeBudgetProgress(percentUsed = 85.0))
        )
        assertNotNull(BudgetRiskRule.evaluate(context))
    }

    @Test
    fun `AverageMonthlySpendRule needs at least 2 months`() {
        val oneMonth = emptyContext().copy(
            monthlyCashFlow = listOf(MonthlyCashFlow(LocalDate.of(2026, 1, 1), money(1000), money(500)))
        )
        assertNull(AverageMonthlySpendRule.evaluate(oneMonth))
    }

    @Test
    fun `AverageMonthlySpendRule averages expense across the given months`() {
        val context = emptyContext().copy(
            monthlyCashFlow = listOf(
                MonthlyCashFlow(LocalDate.of(2026, 1, 1), money(1000), money(400)),
                MonthlyCashFlow(LocalDate.of(2026, 2, 1), money(1000), money(600))
            )
        )
        val insight = AverageMonthlySpendRule.evaluate(context)
        assertNotNull(insight)
        // (400 + 600) / 2 = 500 - the formatted average should appear in the title.
        assertEquals(true, insight!!.title.contains("500"))
    }

    @Test
    fun `TopCategoryThisMonthRule returns null when there is no spending yet`() {
        assertNull(TopCategoryThisMonthRule.evaluate(emptyContext()))
    }

    @Test
    fun `SpendingConcentrationRule needs at least 8 transactions to say anything`() {
        val context = emptyContext().copy(
            currentMonthExpenseTransactions = List(3) { fakeExpenseTransaction(dayOfMonth = 2) }
        )
        assertNull(SpendingConcentrationRule.evaluate(context))
    }

    private fun fakeBudgetProgress(percentUsed: Double) = com.moneta.app.data.repository.BudgetProgress(
        budget = com.moneta.app.data.repository.Budget(
            id = 1,
            scope = com.moneta.app.data.local.entity.BudgetScope.CATEGORY,
            categoryId = 1,
            amount = money(1000),
            period = com.moneta.app.data.local.entity.BudgetPeriod.MONTHLY,
            customPeriodStart = null,
            customPeriodEnd = null,
            alertThresholdPercent = 70,
            isEnabled = true
        ),
        categoryName = "Food",
        spent = money((1000 * percentUsed / 100).toLong()),
        remaining = money((1000 * (100 - percentUsed) / 100).toLong()),
        percentUsed = percentUsed,
        status = com.moneta.app.data.repository.BudgetStatus.NEAR
    )

    private fun fakeExpenseTransaction(dayOfMonth: Int) = com.moneta.app.data.repository.Transaction(
        id = 0,
        type = com.moneta.app.data.local.entity.TransactionType.EXPENSE,
        amount = money(100),
        accountId = 1,
        toAccountId = null,
        categoryId = 1,
        date = LocalDate.of(2026, 3, dayOfMonth),
        timeMinutesSinceMidnight = 600,
        payee = null,
        notes = null,
        paymentMethod = null,
        receiptUri = null
    )
}
