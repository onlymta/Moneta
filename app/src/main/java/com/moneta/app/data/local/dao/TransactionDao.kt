package com.moneta.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneta.app.data.local.entity.TransactionEntity
import com.moneta.app.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

/** Category id + total amount, used for spending-by-category breakdowns. */
data class CategoryTotal(
    val categoryId: Long?,
    val totalMinor: Long
)

@Dao
interface TransactionDao {

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        ORDER BY date DESC, time DESC, id DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0 AND date >= :startEpochDay AND date < :endEpochDayExclusive
        ORDER BY date DESC, time DESC, id DESC
    """)
    fun observeInRange(startEpochDay: Long, endEpochDayExclusive: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("""
        SELECT COALESCE(SUM(amount_minor), 0) FROM transactions
        WHERE is_deleted = 0 AND type = :type
        AND date >= :startEpochDay AND date < :endEpochDayExclusive
    """)
    fun observeTotalForTypeInRange(
        type: TransactionType,
        startEpochDay: Long,
        endEpochDayExclusive: Long
    ): Flow<Long>

    @Query("""
        SELECT category_id AS categoryId, COALESCE(SUM(amount_minor), 0) AS totalMinor
        FROM transactions
        WHERE is_deleted = 0 AND type = :type
        AND date >= :startEpochDay AND date < :endEpochDayExclusive
        GROUP BY category_id
        ORDER BY totalMinor DESC
    """)
    fun observeTotalsByCategory(
        type: TransactionType,
        startEpochDay: Long,
        endEpochDayExclusive: Long
    ): Flow<List<CategoryTotal>>

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND (:accountId IS NULL OR account_id = :accountId OR to_account_id = :accountId)
        AND (:categoryId IS NULL OR category_id = :categoryId)
        AND (:type IS NULL OR type = :type)
        AND date >= :startEpochDay AND date < :endEpochDayExclusive
        AND (:query IS NULL OR payee LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        ORDER BY date DESC, time DESC, id DESC
    """)
    fun observeFiltered(
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        startEpochDay: Long,
        endEpochDayExclusive: Long,
        query: String?
    ): Flow<List<TransactionEntity>>

    /** Used by budget progress (Phase 4): total spent in one category within a date range. */
    @Query("""
        SELECT COALESCE(SUM(amount_minor), 0) FROM transactions
        WHERE is_deleted = 0 AND type = 'EXPENSE' AND category_id = :categoryId
        AND date >= :startEpochDay AND date < :endEpochDayExclusive
    """)
    fun observeTotalForCategoryInRange(
        categoryId: Long,
        startEpochDay: Long,
        endEpochDayExclusive: Long
    ): Flow<Long>

    /**
     * One-shot: this account's net effect (income credits, expense/transfer-out debits,
     * transfer-in credits) from all its non-deleted transactions up to and including
     * [asOfEpochDay]. Used by the net-worth-trend report (Section 17) to reconstruct a
     * historical balance as opening_balance + this value, without storing balance snapshots.
     */
    @Query("""
        SELECT COALESCE(SUM(
            CASE
                WHEN type = 'INCOME' AND account_id = :accountId THEN amount_minor
                WHEN type = 'EXPENSE' AND account_id = :accountId THEN -amount_minor
                WHEN type = 'TRANSFER' AND account_id = :accountId THEN -amount_minor
                WHEN type = 'TRANSFER' AND to_account_id = :accountId THEN amount_minor
                ELSE 0
            END
        ), 0)
        FROM transactions
        WHERE is_deleted = 0 AND date <= :asOfEpochDay
        AND (account_id = :accountId OR to_account_id = :accountId)
    """)
    suspend fun getAccountNetEffectAsOf(accountId: Long, asOfEpochDay: Long): Long

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transactions SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("UPDATE transactions SET is_deleted = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun undoDelete(id: Long, updatedAt: Long)

    // --- Backup/Restore (Phase 8) ---

    @Query("SELECT * FROM transactions")
    suspend fun getAllForBackup(): List<TransactionEntity>

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllForRestore()
}
