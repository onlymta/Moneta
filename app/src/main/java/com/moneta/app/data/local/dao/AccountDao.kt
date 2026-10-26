package com.moneta.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneta.app.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** How many records use an account or a category, used to put the most used first. */
data class UseCount(val id: Long, val uses: Int)

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    /** One-shot variant used by report calculations (Section 17 net worth trend) that don't need live updates. */
    @Query("SELECT * FROM accounts WHERE is_archived = 0 AND include_in_net_worth = 1")
    suspend fun getActiveIncludedInNetWorth(): List<AccountEntity>

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("UPDATE accounts SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("SELECT account_id AS id, COUNT(*) AS uses FROM transactions WHERE is_deleted = 0 GROUP BY account_id")
    suspend fun useCounts(): List<UseCount>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("""
        SELECT COALESCE(SUM(current_balance_minor), 0) FROM accounts
        WHERE is_archived = 0 AND include_in_net_worth = 1
    """)
    fun observeTotalNetWorthMinor(): Flow<Long>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET opening_balance_minor = :opening, current_balance_minor = current_balance_minor + :delta WHERE id = :id")
    suspend fun updateOpeningBalance(id: Long, opening: Long, delta: Long)

    @Query("UPDATE accounts SET is_archived = 1 WHERE id = :accountId")
    suspend fun archive(accountId: Long)

    /**
     * Only ever called from TransactionRepository's single balance-mutation
     * write path (ARCHITECTURE.md Section 6/32) - never called directly from
     * a ViewModel or UI layer.
     */
    @Query("UPDATE accounts SET current_balance_minor = current_balance_minor + :deltaMinor WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Long, deltaMinor: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE (account_id = :accountId OR to_account_id = :accountId) AND is_deleted = 0")
    suspend fun transactionCountForAccount(accountId: Long): Int

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteHard(accountId: Long)

    // --- Backup/Restore (Phase 8) ---

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Query("DELETE FROM accounts")
    suspend fun deleteAllForRestore()
}
