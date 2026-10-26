package com.moneta.app.data.repository

import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.BudgetDao
import com.moneta.app.data.local.entity.BudgetEntity
import com.moneta.app.data.local.entity.BudgetPeriod
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {

    // Small in-memory cache so resolving a budget's category name doesn't re-query the
    // database on every single Flow emission - invalidation isn't needed since category
    // names rarely change and a rename will show correctly on the next process start,
    // which is an acceptable trade-off for this Phase 4 slice.
    private val categoryNameCacheMutex = Mutex()
    private val categoryNameCache = mutableMapOf<Long, String>()

    fun observeEnabledBudgets(): Flow<List<Budget>> =
        budgetDao.observeEnabled().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Budget? = budgetDao.getById(id)?.toDomain()

    /** Every budget of every month (each month's budgets are separate rows). */
    fun observeAllBudgets(): Flow<List<Budget>> =
        budgetDao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * Budgets used to repeat every month. Now each month has its own set, so any
     * leftover "every month" budget is turned into this month's budget once.
     */
    suspend fun convertLegacyMonthlyBudgets() {
        val month = LocalDate.now().withDayOfMonth(1)
        budgetDao.observeAll().first()
            .filter { it.period == BudgetPeriod.MONTHLY }
            .forEach {
                budgetDao.update(
                    it.copy(
                        period = BudgetPeriod.CUSTOM,
                        customPeriodStart = month.toEpochDay(),
                        customPeriodEnd = month.withDayOfMonth(month.lengthOfMonth()).toEpochDay()
                    )
                )
            }
    }

    /** Creates a category budget that belongs to one specific month (the month containing [month]). */
    suspend fun createCategoryBudgetForMonth(categoryId: Long, amount: Money, month: LocalDate) {
        val start = month.withDayOfMonth(1)
        budgetDao.insert(
            BudgetEntity(
                scope = BudgetScope.CATEGORY,
                categoryId = categoryId,
                amountMinor = amount.minorUnits,
                currencyCode = amount.currencyCode,
                period = BudgetPeriod.CUSTOM,
                customPeriodStart = start.toEpochDay(),
                customPeriodEnd = start.withDayOfMonth(start.lengthOfMonth()).toEpochDay()
            )
        )
    }

    /** Sets a category's budget for one month: updates it if it exists there, otherwise creates it. */
    suspend fun setCategoryBudgetForMonth(categoryId: Long, amount: Money, month: LocalDate) {
        val start = month.withDayOfMonth(1)
        val existing = budgetDao.observeAll().first().firstOrNull {
            it.scope == BudgetScope.CATEGORY && it.categoryId == categoryId &&
                it.period == BudgetPeriod.CUSTOM && it.customPeriodStart == start.toEpochDay()
        }
        if (existing != null) {
            budgetDao.update(existing.copy(amountMinor = amount.minorUnits, isEnabled = true))
        } else {
            createCategoryBudgetForMonth(categoryId, amount, start)
        }
    }

    /** Removes a category's budget for one month, if it has one. */
    suspend fun clearCategoryBudgetForMonth(categoryId: Long, month: LocalDate) {
        val start = month.withDayOfMonth(1)
        budgetDao.observeAll().first()
            .filter {
                it.scope == BudgetScope.CATEGORY && it.categoryId == categoryId &&
                    it.period == BudgetPeriod.CUSTOM && it.customPeriodStart == start.toEpochDay()
            }
            .forEach { budgetDao.delete(it.id) }
    }

    /** Copies every budget of [fromMonth] into [toMonth] (skipping ones already there). Returns how many were added. */
    suspend fun copyBudgets(fromMonth: LocalDate, toMonth: LocalDate): Int {
        val from = fromMonth.withDayOfMonth(1)
        val to = toMonth.withDayOfMonth(1)
        val all = budgetDao.observeAll().first().filter { it.period == BudgetPeriod.CUSTOM }
        val source = all.filter { it.customPeriodStart == from.toEpochDay() }
        val existing = all.filter { it.customPeriodStart == to.toEpochDay() }
        var added = 0
        source.forEach { b ->
            val already = existing.any { it.scope == b.scope && it.categoryId == b.categoryId }
            if (!already) {
                budgetDao.insert(
                    b.copy(
                        id = 0,
                        customPeriodStart = to.toEpochDay(),
                        customPeriodEnd = to.withDayOfMonth(to.lengthOfMonth()).toEpochDay()
                    )
                )
                added += 1
            }
        }
        return added
    }

    /**
     * The single place budget progress is computed (Section 17: "the same
     * 'spent' number must be reused between the Budget screen, Home dashboard
     * snippet, and Reports so numbers never disagree") - it reuses
     * TransactionRepository's existing queries rather than running its own SQL.
     */
    fun observeProgress(): Flow<List<BudgetProgress>> =
        observeEnabledBudgets().map { all ->
            val today = LocalDate.now()
            all.filter { today in periodRangeFor(it) }
        }.flatMapLatest { budgets ->
            if (budgets.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(budgets.map { observeSingleProgress(it) }) { it.toList() }
            }
        }

    private fun observeSingleProgress(budget: Budget): Flow<BudgetProgress> {
        val range = periodRangeFor(budget)
        val spentFlow: Flow<Money> = when (budget.scope) {
            BudgetScope.OVERALL ->
                transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, range, budget.amount.currencyCode)
            BudgetScope.CATEGORY -> {
                val categoryId = budget.categoryId
                if (categoryId == null) {
                    flowOf(Money.zero(budget.amount.currencyCode))
                } else {
                    transactionRepository.observeTotalForCategoryInRange(categoryId, range, budget.amount.currencyCode)
                }
            }
        }
        return spentFlow.distinctUntilChanged().map { spent -> toProgress(budget, spent) }
    }

    private suspend fun categoryNameFor(categoryId: Long): String? {
        categoryNameCache[categoryId]?.let { return it }
        return categoryNameCacheMutex.withLock {
            categoryNameCache[categoryId]
                ?: categoryRepository.getById(categoryId)?.name?.also { categoryNameCache[categoryId] = it }
        }
    }

    private suspend fun toProgress(budget: Budget, spent: Money): BudgetProgress {
        val categoryName = budget.categoryId?.let { categoryNameFor(it) }
        val remaining = budget.amount - spent
        val percentUsed = spent.percentOf(budget.amount)
        val status = when {
            percentUsed == null -> BudgetStatus.UNDER
            percentUsed >= 100.0 -> BudgetStatus.OVER
            percentUsed >= budget.alertThresholdPercent -> BudgetStatus.NEAR
            else -> BudgetStatus.UNDER
        }
        return BudgetProgress(budget, categoryName, spent, remaining, percentUsed, status)
    }

    private fun periodRangeFor(budget: Budget): DateRange = when (budget.period) {
        BudgetPeriod.MONTHLY -> DateRange.currentMonth()
        BudgetPeriod.CUSTOM -> DateRange.custom(
            start = budget.customPeriodStart ?: LocalDate.now(),
            endInclusive = budget.customPeriodEnd ?: LocalDate.now()
        )
    }

    suspend fun createOverallBudget(amount: Money, alertThresholdPercent: Int = 90) {
        budgetDao.insert(
            BudgetEntity(
                scope = BudgetScope.OVERALL,
                categoryId = null,
                amountMinor = amount.minorUnits,
                currencyCode = amount.currencyCode,
                period = BudgetPeriod.MONTHLY,
                alertThresholdPercent = alertThresholdPercent
            )
        )
    }

    suspend fun createCategoryBudget(categoryId: Long, amount: Money, alertThresholdPercent: Int = 90) {
        budgetDao.insert(
            BudgetEntity(
                scope = BudgetScope.CATEGORY,
                categoryId = categoryId,
                amountMinor = amount.minorUnits,
                currencyCode = amount.currencyCode,
                period = BudgetPeriod.MONTHLY,
                alertThresholdPercent = alertThresholdPercent
            )
        )
    }

    suspend fun updateAmount(budgetId: Long, amount: Money, alertThresholdPercent: Int) {
        val existing = budgetDao.getById(budgetId) ?: return
        budgetDao.update(existing.copy(amountMinor = amount.minorUnits, alertThresholdPercent = alertThresholdPercent))
    }

    suspend fun setEnabled(budgetId: Long, isEnabled: Boolean) {
        val existing = budgetDao.getById(budgetId) ?: return
        budgetDao.update(existing.copy(isEnabled = isEnabled))
    }

    suspend fun delete(budgetId: Long) = budgetDao.delete(budgetId)

    private fun BudgetEntity.toDomain() = Budget(
        id = id,
        scope = scope,
        categoryId = categoryId,
        amount = Money(amountMinor, currencyCode),
        period = period,
        customPeriodStart = customPeriodStart?.let { LocalDate.ofEpochDay(it) },
        customPeriodEnd = customPeriodEnd?.let { LocalDate.ofEpochDay(it) },
        alertThresholdPercent = alertThresholdPercent,
        isEnabled = isEnabled
    )
}
