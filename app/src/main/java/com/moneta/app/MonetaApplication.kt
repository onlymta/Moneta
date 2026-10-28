package com.moneta.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.moneta.app.data.local.DefaultCategorySeed
import com.moneta.app.data.local.dao.CategoryDao
import com.moneta.app.data.repository.RecurringTransactionRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MonetaApplication : Application() {

    @Inject lateinit var categoryDao: CategoryDao
    @Inject lateinit var recurringTransactionRepository: RecurringTransactionRepository
    @Inject lateinit var autoBackupManager: com.moneta.app.data.backup.AutoBackupManager
    @Inject lateinit var plusRepository: com.moneta.app.data.plus.PlusRepository
    @Inject lateinit var billingManager: com.moneta.app.data.plus.BillingManager

    override fun onCreate() {
        super.onCreate()
        try {
            com.moneta.app.reminder.BillReminderScheduler.ensureScheduled(this)
        } catch (e: Exception) {
            // Alarms being unavailable must never stop the app.
        }
        // Both run on the process lifecycle scope rather than blocking app
        // startup (Section 42: startup performance matters for Play Store
        // quality).
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            // Re-checks the Play purchase.
            try {
                billingManager.refresh()
            } catch (e: Exception) {
                // Billing being unavailable must never stop the app.
            }
        }
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            seedDefaultCategoriesIfNeeded()
        }
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            // Catches up any recurring transactions that came due since the
            // app was last opened (Section 15). Running this on every
            // foreground start - rather than only via a background
            // WorkManager job - is the deliberate scope for this phase; see
            // the project README for why a periodic background job (so
            // occurrences appear even without opening the app) is the
            // natural next step rather than something faked here.
            recurringTransactionRepository.generateDueOccurrences()
        }
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            // Automatic local backup, when the chosen daily/weekly interval has passed.
            try {
                autoBackupManager.runIfDue()
            } catch (e: Exception) {
                // A failed automatic backup must never stop the app from starting.
            }
        }
    }

    private suspend fun seedDefaultCategoriesIfNeeded() {
        // A one-shot check: only seed if the table is genuinely empty, so this
        // is safe to run on every app start without duplicating categories.
        val existing = categoryDao.observeAll().first()
        if (existing.isEmpty()) {
            (DefaultCategorySeed.expenseCategories() + DefaultCategorySeed.incomeCategories())
                .forEach { categoryDao.insert(it) }
        }
    }
}
