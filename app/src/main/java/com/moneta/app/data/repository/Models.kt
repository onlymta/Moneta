package com.moneta.app.data.repository

import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.TransactionType
import java.time.LocalDate

/**
 * Domain-layer models (as distinct from Room @Entity classes) so ViewModels
 * and use-cases never depend on persistence annotations, and so [Money] is
 * used everywhere above the DAO boundary instead of raw Long minor units.
 */

data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val icon: String,
    val colorHex: String,
    val openingBalance: Money,
    val currentBalance: Money,
    val includeInNetWorth: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int
)

data class Category(
    val id: Long,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val colorHex: String,
    val parentCategoryId: Long?,
    val sortOrder: Int,
    val isDefault: Boolean,
    val isArchived: Boolean
)

data class Transaction(
    val id: Long,
    val type: TransactionType,
    val amount: Money,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val date: LocalDate,
    val timeMinutesSinceMidnight: Int,
    val payee: String?,
    val notes: String?,
    val paymentMethod: String?,
    val receiptUri: String?,
    val tagIds: List<Long> = emptyList()
)

/** Input used when creating/editing a transaction from the Quick Add flow (Section 7). */
data class TransactionDraft(
    val type: TransactionType,
    val amount: Money,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val date: LocalDate,
    val timeMinutesSinceMidnight: Int,
    val payee: String? = null,
    val notes: String? = null,
    val paymentMethod: String? = null,
    val receiptUri: String? = null,
    val tagIds: List<Long> = emptyList()
) {
    init {
        when (type) {
            TransactionType.TRANSFER -> {
                require(toAccountId != null && toAccountId != accountId) {
                    "A transfer needs a distinct destination account."
                }
                require(categoryId == null) { "Transfers must not have a category." }
            }
            else -> require(categoryId != null) { "Income/expense transactions must have a category." }
        }
    }
}

// --- Budgets (Phase 4) ---

enum class BudgetStatus { UNDER, NEAR, OVER }

data class Budget(
    val id: Long,
    val scope: com.moneta.app.data.local.entity.BudgetScope,
    val categoryId: Long?,
    val amount: Money,
    val period: com.moneta.app.data.local.entity.BudgetPeriod,
    val customPeriodStart: LocalDate?,
    val customPeriodEnd: LocalDate?,
    val alertThresholdPercent: Int,
    val isEnabled: Boolean,
    val isComplete: Boolean = false
)

/** A budget combined with how much has actually been spent against it for the current period. */
data class BudgetProgress(
    val budget: Budget,
    val categoryName: String?,
    val spent: Money,
    val remaining: Money,
    val percentUsed: Double?,
    val status: BudgetStatus
)

// --- Goals (Phase 6) ---

data class Goal(
    val id: Long,
    val name: String,
    val icon: String,
    val targetAmount: Money,
    val currentAmount: Money,
    val targetDate: LocalDate?,
    val linkedAccountId: Long?,
    val isArchived: Boolean
) {
    val remaining: Money get() = (targetAmount - currentAmount).let { if (it.isNegative) Money.zero(it.currencyCode) else it }
    val percentComplete: Double? get() = currentAmount.percentOf(targetAmount)?.coerceIn(0.0, 100.0)
    val isComplete: Boolean get() = currentAmount >= targetAmount
}

// --- Bills (Phase 6) ---

enum class BillUrgency { OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING, PAID }

data class Bill(
    val id: Long,
    val name: String,
    val amount: Money,
    val dueDate: LocalDate,
    val accountId: Long?,
    val categoryId: Long?,
    val status: com.moneta.app.data.local.entity.BillStatus,
    val paidTransactionId: Long?,
    val reminderDaysBefore: Int = -1,
    val notes: String? = null
)

// --- Recurring transactions ---

data class RecurringTransaction(
    val id: Long,
    val templateType: TransactionType,
    val amount: Money,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val frequency: com.moneta.app.data.local.entity.RecurrenceFrequency,
    val intervalCount: Int,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextOccurrenceDate: LocalDate,
    val reminderEnabled: Boolean,
    val reminderOffsetDays: Int,
    val isActive: Boolean,
    val notes: String?,
    val payee: String?,
    val anchorDay: Int = 0
)
