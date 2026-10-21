package com.moneta.app.domain.usecase

import com.moneta.app.data.local.dao.CategoryTotal
import com.moneta.app.data.local.entity.BudgetPeriod
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.repository.Budget
import com.moneta.app.data.repository.Category
import java.time.LocalDate

/**
 * Which category each recorded amount is counted under.
 * Sub-categories off: everything counts under its top-level ancestor (Food, not Food > Groceries).
 * Sub-categories on: every category stands on its own, with only its own transactions.
 */
fun countedUnder(categories: List<Category>, subcategoriesEnabled: Boolean): Map<Long, Long> {
    if (subcategoriesEnabled) return categories.associate { it.id to it.id }
    val byId = categories.associateBy { it.id }
    fun rootOf(c: Category): Long {
        var current = c
        var guard = 0
        while (current.parentCategoryId != null && guard < 20) {
            current = byId[current.parentCategoryId] ?: break
            guard++
        }
        return current.id
    }
    return categories.associate { it.id to rootOf(it) }
}

/** Adds category totals up under the category they are counted under (see [countedUnder]). */
fun rollUp(totals: List<CategoryTotal>, categories: List<Category>, subcategoriesEnabled: Boolean): List<CategoryTotal> {
    val map = countedUnder(categories, subcategoriesEnabled)
    val sums = LinkedHashMap<Long?, Long>()
    totals.forEach { row ->
        val key = row.categoryId?.let { map[it] ?: it }
        sums[key] = (sums[key] ?: 0L) + row.totalMinor
    }
    return sums.map { (id, minor) -> CategoryTotal(id, minor) }.sortedByDescending { it.totalMinor }
}

/** Spent per counted-under category, as a plain map. */
fun spentMap(totals: List<CategoryTotal>, categories: List<Category>, subcategoriesEnabled: Boolean): Map<Long, Long> =
    rollUp(totals, categories, subcategoriesEnabled).mapNotNull { row -> row.categoryId?.let { it to row.totalMinor } }.toMap()

/** The category budgets set for the month starting [monthStart]: category id to amount (minor units). */
fun monthCategoryBudgets(all: List<Budget>, monthStart: LocalDate): Map<Long, Long> = all
    .filter {
        it.isEnabled && it.scope == BudgetScope.CATEGORY && it.categoryId != null &&
            it.period == BudgetPeriod.CUSTOM && it.customPeriodStart == monthStart
    }
    .associate { it.categoryId!! to it.amount.minorUnits }

/** Budgets plus, when roll-over is on, what was left unspent of last month's budget in each category. */
fun effectiveBudgets(
    thisMonth: Map<Long, Long>,
    lastMonth: Map<Long, Long>,
    lastMonthSpent: Map<Long, Long>,
    rollover: Boolean
): Map<Long, Long> = thisMonth.mapValues { (id, base) ->
    val left = if (rollover) (lastMonth[id] ?: 0L) - (lastMonthSpent[id] ?: 0L) else 0L
    base + (if (left > 0L) left else 0L)
}

/**
 * The budget picture for one period. Income plays no part in it.
 * [yetToSpend] is what is still unspent inside budgeted categories (never negative per category).
 * [projectedSpend] is budgeted-category spending carried forward at the pace so far (or just what was spent,
 * for a month that is over); [expectedVariance] is that minus the total budget: positive means over budget.
 */
data class BudgetSummary(
    val totalBudget: Long,
    val spent: Long,
    val yetToSpend: Long,
    val projectedSpend: Long,
    val expectedVariance: Long
) {
    val remaining: Long get() = totalBudget - spent
}

fun summarizeBudgets(
    budgets: Map<Long, Long>,
    spent: Map<Long, Long>,
    daysElapsed: Int,
    totalDays: Int,
    isCurrentMonth: Boolean
): BudgetSummary {
    var total = 0L
    var spentSum = 0L
    var yet = 0L
    var projected = 0L
    budgets.forEach { (id, budget) ->
        val s = spent[id] ?: 0L
        total += budget
        spentSum += s
        yet += maxOf(0L, budget - s)
        val pace = if (isCurrentMonth && daysElapsed > 0) s * totalDays / daysElapsed else s
        projected += maxOf(s, pace)
    }
    return BudgetSummary(total, spentSum, yet, projected, projected - total)
}
