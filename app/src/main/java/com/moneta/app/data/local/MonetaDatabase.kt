package com.moneta.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moneta.app.data.local.dao.AccountDao
import com.moneta.app.data.local.dao.BillDao
import com.moneta.app.data.local.dao.BudgetDao
import com.moneta.app.data.local.dao.CategoryDao
import com.moneta.app.data.local.dao.GoalDao
import com.moneta.app.data.local.dao.RecurringTransactionDao
import com.moneta.app.data.local.dao.TagDao
import com.moneta.app.data.local.dao.TransactionDao
import com.moneta.app.data.local.entity.AccountEntity
import com.moneta.app.data.local.entity.BillEntity
import com.moneta.app.data.local.entity.BudgetEntity
import com.moneta.app.data.local.entity.CategoryEntity
import com.moneta.app.data.local.entity.GoalEntity
import com.moneta.app.data.local.entity.RecurringTransactionEntity
import com.moneta.app.data.local.entity.TagEntity
import com.moneta.app.data.local.entity.TransactionEntity
import com.moneta.app.data.local.entity.TransactionTagCrossRef

/**
 * Phase 1-9+ schema: Account, Category, Transaction, Tag/TransactionTag,
 * Budget (v2), Goal/Bill (v3), and RecurringTransaction (v4). Every change so
 * far has been a purely additive Room Migration (Section 45: never destroy
 * existing user data because the database model changed).
 *
 * version = 4 (Migration(3, 4) adds `recurring_transactions` - see Migrations.kt).
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        BudgetEntity::class,
        GoalEntity::class,
        BillEntity::class,
        RecurringTransactionEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MonetaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun tagDao(): TagDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun billDao(): BillDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao

    companion object {
        const val DATABASE_NAME = "moneta.db"
    }
}
