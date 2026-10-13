package com.moneta.app.domain.insights

import kotlin.math.abs
import kotlin.math.roundToInt

/** "You spent 18% more on Food this month than last month." - the single biggest category mover. */
object CategoryMonthOverMonthRule : InsightRule {
    private const val MIN_PERCENT_CHANGE = 15.0
    private const val MIN_AMOUNT_MINOR = 100_00L // ignore tiny categories where a % swing is just noise

    override fun evaluate(context: InsightContext): Insight? {
        val previousByCategory = context.previousMonthExpenseByCategory.associateBy { it.categoryId }
        var best: Triple<String, Double, Boolean>? = null // name, absPercentChange, isIncrease

        context.currentMonthExpenseByCategory.forEach { current ->
            val previous = previousByCategory[current.categoryId] ?: return@forEach
            if (previous.amount.minorUnits < MIN_AMOUNT_MINOR) return@forEach
            val percentChange = current.amount.percentOf(previous.amount)?.let { it - 100.0 } ?: return@forEach
            if (abs(percentChange) < MIN_PERCENT_CHANGE) return@forEach
            if (best == null || abs(percentChange) > best!!.second) {
                best = Triple(current.name, abs(percentChange), percentChange > 0)
            }
        }

        val (name, percentChange, isIncrease) = best ?: return null
        val direction = if (isIncrease) "more" else "less"
        return Insight(
            id = "category_mom_$name",
            severity = if (isIncrease) InsightSeverity.NOTICE else InsightSeverity.INFO,
            title = "$name spending is ${percentChange.roundToInt()}% $direction than last month",
            body = "You've spent ${percentChange.roundToInt()}% $direction on $name this month compared to last month."
        )
    }
}

/** "Your transportation expenses have increased for three consecutive months." */
object ConsecutiveIncreaseRule : InsightRule {
    override fun evaluate(context: InsightContext): Insight? {
        if (context.monthlyCashFlow.size < 3) return null
        val lastThree = context.monthlyCashFlow.takeLast(3)
        val increasing = lastThree.zipWithNext().all { (a, b) -> b.expense > a.expense }
        if (!increasing) return null
        return Insight(
            id = "consecutive_increase",
            severity = InsightSeverity.WARNING,
            title = "Spending has risen for 3 months in a row",
            body = "Your overall monthly spending has increased for three consecutive months. Worth a look at what's driving it."
        )
    }
}

/** "Your average monthly spending is Rs. 74,200." */
object AverageMonthlySpendRule : InsightRule {
    override fun evaluate(context: InsightContext): Insight? {
        if (context.monthlyCashFlow.size < 2) return null
        val currency = context.currencyCode
        val totalMinor = context.monthlyCashFlow.sumOf { it.expense.minorUnits }
        val average = com.moneta.app.core.money.Money(totalMinor / context.monthlyCashFlow.size, currency)
        return Insight(
            id = "average_monthly_spend",
            severity = InsightSeverity.INFO,
            title = "Average monthly spending: ${average.formatted()}",
            body = "Based on your last ${context.monthlyCashFlow.size} months, you spend an average of ${average.formatted()} per month."
        )
    }
}

/** "Your highest spending category this month is Food." */
object TopCategoryThisMonthRule : InsightRule {
    override fun evaluate(context: InsightContext): Insight? {
        val top = context.currentMonthExpenseByCategory.maxByOrNull { it.amount.minorUnits } ?: return null
        if (top.amount.isZero) return null
        return Insight(
            id = "top_category",
            severity = InsightSeverity.INFO,
            title = "${top.name} is your top expense this month",
            body = "You've spent ${top.amount.formatted()} on ${top.name} so far this month - ${top.percentOfTotal.roundToInt()}% of your total spending."
        )
    }
}

/** "You are currently spending 72% of your Food budget." / budget-risk warning. */
object BudgetRiskRule : InsightRule {
    override fun evaluate(context: InsightContext): Insight? {
        val riskiest = context.budgetProgress
            .filter { it.percentUsed != null }
            .maxByOrNull { it.percentUsed!! } ?: return null
        val percent = riskiest.percentUsed ?: return null
        if (percent < 70.0) return null

        val label = riskiest.categoryName ?: "overall"
        val severity = if (percent >= 100.0) InsightSeverity.WARNING else InsightSeverity.NOTICE
        val title = if (percent >= 100.0) {
            "$label budget exceeded"
        } else {
            "You're at ${percent.roundToInt()}% of your $label budget"
        }
        return Insight(
            id = "budget_risk_${riskiest.budget.id}",
            severity = severity,
            title = title,
            body = "You've spent ${riskiest.spent.formatted()} of your ${riskiest.budget.amount.formatted()} $label budget this period."
        )
    }
}

/** "You saved Rs. 12,500 more this month than last month." */
object SavingsTrendRule : InsightRule {
    override fun evaluate(context: InsightContext): Insight? {
        if (context.monthlyCashFlow.size < 2) return null
        val (previous, current) = context.monthlyCashFlow.takeLast(2)
        val previousSavings = previous.net
        val currentSavings = current.net
        val delta = currentSavings - previousSavings
        if (delta.abs().minorUnits < 100_00L) return null // ignore trivial swings

        val direction = if (delta.isPositive) "more" else "less"
        return Insight(
            id = "savings_trend",
            severity = InsightSeverity.INFO,
            title = "You saved ${delta.abs().formatted()} $direction than last month",
            body = "Your net savings this month are ${currentSavings.formatted()}, compared to ${previousSavings.formatted()} last month."
        )
    }
}

/** "The majority of your expenses occur between the 1st and 10th of the month." */
object SpendingConcentrationRule : InsightRule {
    override fun evaluate(context: InsightContext): Insight? {
        val transactions = context.currentMonthExpenseTransactions
        if (transactions.size < 8) return null // too few data points to say anything meaningful
        val totalMinor = transactions.sumOf { it.amount.minorUnits }
        if (totalMinor == 0L) return null
        val firstTenDaysMinor = transactions.filter { it.date.dayOfMonth <= 10 }.sumOf { it.amount.minorUnits }
        val percent = (firstTenDaysMinor.toDouble() / totalMinor.toDouble()) * 100.0
        if (percent < 60.0) return null
        return Insight(
            id = "spending_concentration",
            severity = InsightSeverity.INFO,
            title = "Most spending happens early in the month",
            body = "${percent.roundToInt()}% of this month's spending happened between the 1st and the 10th."
        )
    }
}

/** All Phase 7 rules, in the order they're checked - display order is decided by priority, not this list order. */
val ALL_INSIGHT_RULES: List<InsightRule> = listOf(
    BudgetRiskRule,
    ConsecutiveIncreaseRule,
    CategoryMonthOverMonthRule,
    SavingsTrendRule,
    TopCategoryThisMonthRule,
    SpendingConcentrationRule,
    AverageMonthlySpendRule
)
