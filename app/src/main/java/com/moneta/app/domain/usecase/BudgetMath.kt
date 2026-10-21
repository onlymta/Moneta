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

/** Child ids per parent id. */
fun childIndex(categories: List<Category>): Map<Long, List<Long>> =
    categories.filter { it.parentCategoryId != null }.groupBy({ it.parentCategoryId!! }, { it.id })

/**
 * Totals for every category including everything beneath it.
 * [leafOnly]: a category that has sub-categories ignores its own value and is just the sum of those beneath
 * (used for budgets, where a parent has no budget of its own). Otherwise a parent adds its own value too (used for spending).
 */
fun treeTotals(categories: List<Category>, direct: Map<Long, Long>, leafOnly: Boolean): Map<Long, Long> {
    val kids = childIndex(categories)
    val memo = HashMap<Long, Long>()
    fun total(id: Long, depth: Int): Long {
        memo[id]?.let { return it }
        val children = kids[id].orEmpty()
        val below = if (depth > 25) 0L else children.sumOf { total(it, depth + 1) }
        val own = direct[id] ?: 0L
        val result = if (children.isEmpty()) own else if (leafOnly) below else own + below
        memo[id] = result
        return result
    }
    categories.forEach { total(it.id, 0) }
    return memo
}

/** Every category beneath [id], not including it. */
fun descendantIds(categories: List<Category>, id: Long): Set<Long> {
    val kids = childIndex(categories)
    val out = LinkedHashSet<Long>()
    fun walk(x: Long, depth: Int) {
        if (depth > 25) return
        kids[x].orEmpty().forEach { if (out.add(it)) walk(it, depth + 1) }
    }
    walk(id, 0)
    return out
}

/**
 * The budget "units" fed to [summarizeBudgets]: with sub-categories on, each lowest-level category that has a budget,
 * plus the spending made directly on a parent whose group is budgeted; with them off, the top-level categories.
 */
fun budgetUnits(
    categories: List<Category>,
    ownBudgets: Map<Long, Long>,
    totals: List<CategoryTotal>,
    subcategoriesEnabled: Boolean
): Pair<Map<Long, Long>, Map<Long, Long>> {
    if (!subcategoriesEnabled) {
        return ownBudgets to spentMap(totals, categories, false)
    }
    val direct = totals.mapNotNull { row -> row.categoryId?.let { it to row.totalMinor } }.toMap()
    val kids = childIndex(categories)
    val groupBudget = treeTotals(categories, ownBudgets, leafOnly = true)
    val budgets = HashMap<Long, Long>()
    val spent = HashMap<Long, Long>()
    fun inBudgetedGroup(c: Category): Boolean {
        var cur: Category? = c
        val byId = categories.associateBy { it.id }
        var guard = 0
        while (cur != null && cur.parentCategoryId != null && guard < 25) {
            cur = byId[cur.parentCategoryId]
            guard++
        }
        return cur != null && (groupBudget[cur.id] ?: 0L) > 0L
    }
    categories.forEach { c ->
        val isLeaf = kids[c.id].isNullOrEmpty()
        if (isLeaf) {
            val b = ownBudgets[c.id]
            if (b != null) {
                budgets[c.id] = b
                spent[c.id] = direct[c.id] ?: 0L
            }
        } else if ((direct[c.id] ?: 0L) > 0L && inBudgetedGroup(c)) {
            budgets[c.id] = 0L
            spent[c.id] = direct[c.id] ?: 0L
        }
    }
    return budgets to spent
}
