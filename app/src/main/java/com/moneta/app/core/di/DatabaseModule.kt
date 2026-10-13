package com.moneta.app.core.di

import android.content.Context
import androidx.room.Room
import com.moneta.app.data.local.ALL_MIGRATIONS
import com.moneta.app.data.local.MonetaDatabase
import com.moneta.app.data.local.dao.AccountDao
import com.moneta.app.data.local.dao.BillDao
import com.moneta.app.data.local.dao.BudgetDao
import com.moneta.app.data.local.dao.CategoryDao
import com.moneta.app.data.local.dao.GoalDao
import com.moneta.app.data.local.dao.RecurringTransactionDao
import com.moneta.app.data.local.dao.TagDao
import com.moneta.app.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MonetaDatabase =
        Room.databaseBuilder(context, MonetaDatabase::class.java, MonetaDatabase.DATABASE_NAME)
            // Every schema change ships an explicit Migration (Section 45) -
            // fallbackToDestructiveMigration must never be enabled once real
            // user data exists.
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideAccountDao(db: MonetaDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCategoryDao(db: MonetaDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTransactionDao(db: MonetaDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideTagDao(db: MonetaDatabase): TagDao = db.tagDao()

    @Provides
    fun provideBudgetDao(db: MonetaDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideGoalDao(db: MonetaDatabase): GoalDao = db.goalDao()

    @Provides
    fun provideBillDao(db: MonetaDatabase): BillDao = db.billDao()

    @Provides
    fun provideRecurringTransactionDao(db: MonetaDatabase): RecurringTransactionDao = db.recurringTransactionDao()
}
