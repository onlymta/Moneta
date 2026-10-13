package com.moneta.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneta.app.data.local.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions WHERE is_active = 1 ORDER BY next_occurrence_date ASC")
    fun observeActive(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions ORDER BY next_occurrence_date ASC")
    fun observeAll(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: Long): RecurringTransactionEntity?

    /** One-shot: every active rule whose next occurrence is due, used by the generation pass. */
    @Query("SELECT * FROM recurring_transactions WHERE is_active = 1 AND next_occurrence_date <= :todayEpochDay")
    suspend fun getDue(todayEpochDay: Long): List<RecurringTransactionEntity>

    @Insert
    suspend fun insert(recurring: RecurringTransactionEntity): Long

    @Update
    suspend fun update(recurring: RecurringTransactionEntity)

    @Query("UPDATE recurring_transactions SET next_occurrence_date = :nextDate WHERE id = :id")
    suspend fun advanceNextOccurrence(id: Long, nextDate: Long)

    @Query("UPDATE recurring_transactions SET is_active = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * The idempotency check (Section 15): before generating an occurrence for
     * (recurringId, date), the repository confirms one doesn't already exist.
     * Under normal operation nextOccurrenceDate always advances past a date
     * once it's used, so this should never find a match - it's a defensive
     * guard against double-generation if generation were ever run
     * concurrently or re-triggered on a half-updated row.
     */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE recurring_transaction_id = :recurringId AND date = :occurrenceDate AND is_deleted = 0
    """)
    suspend fun occurrenceAlreadyExists(recurringId: Long, occurrenceDate: Long): Int

    // --- Backup/Restore ---

    @Query("SELECT * FROM recurring_transactions")
    suspend fun getAllForBackup(): List<RecurringTransactionEntity>

    @Insert
    suspend fun insertAll(recurringTransactions: List<RecurringTransactionEntity>): List<Long>

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAllForRestore()
}
