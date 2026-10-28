package com.moneta.app.data.backup

import androidx.room.withTransaction
import com.moneta.app.data.local.DefaultCategorySeed
import com.moneta.app.data.local.MonetaDatabase
import com.moneta.app.data.local.dao.AccountDao
import com.moneta.app.data.local.dao.BillDao
import com.moneta.app.data.local.dao.BudgetDao
import com.moneta.app.data.local.dao.CategoryDao
import com.moneta.app.data.local.dao.GoalDao
import com.moneta.app.data.local.dao.RecurringTransactionDao
import com.moneta.app.data.local.dao.TagDao
import com.moneta.app.data.local.dao.TransactionDao
import com.moneta.app.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "start over" actions in Settings. None of them touch Plus, the trial or a purchase: those are kept in a
 * separate file on purpose, so wiping data can never be used to get a new free trial.
 */
@Singleton
class DataResetManager @Inject constructor(
    private val database: MonetaDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val billDao: BillDao,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val settingsRepository: SettingsRepository
) {

    /** Deletes every transaction (and its tags). Accounts, categories, budgets, goals and bills stay. */
    suspend fun deleteTransactions() {
        database.withTransaction {
            tagDao.deleteAllCrossRefsForRestore()
            transactionDao.deleteAllForRestore()
        }
    }

    /** Puts all settings back to their defaults. Your data is not touched. */
    suspend fun resetSettings() {
        settingsRepository.resetAll(keepOnboarding = true)
    }

    /** Deletes all data and all settings, brings back the default categories, and restarts the welcome flow. */
    suspend fun deleteEverything() {
        database.withTransaction {
            // Children before parents.
            recurringTransactionDao.deleteAllForRestore()
            billDao.deleteAllForRestore()
            budgetDao.deleteAllForRestore()
            tagDao.deleteAllCrossRefsForRestore()
            tagDao.deleteAllTagsForRestore()
            transactionDao.deleteAllForRestore()
            goalDao.deleteAllForRestore()
            categoryDao.deleteAllForRestore()
            accountDao.deleteAllForRestore()
            (DefaultCategorySeed.expenseCategories() + DefaultCategorySeed.incomeCategories())
                .forEach { categoryDao.insert(it) }
        }
        settingsRepository.resetAll(keepOnboarding = false)
    }
}
