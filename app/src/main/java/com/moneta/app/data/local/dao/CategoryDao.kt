package com.moneta.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneta.app.data.local.entity.CategoryEntity
import com.moneta.app.data.local.entity.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE type = :type AND is_archived = 0 ORDER BY sort_order ASC")
    fun observeByType(type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_archived = 0 ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("SELECT category_id AS id, COUNT(*) AS uses FROM transactions WHERE is_deleted = 0 AND category_id IS NOT NULL GROUP BY category_id")
    suspend fun useCounts(): List<UseCount>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun transactionCountForCategory(categoryId: Long): Int

    /** Bulk-reassigns transactions before a category is safely deleted (Section 12/6). */
    @Query("UPDATE transactions SET category_id = :newCategoryId WHERE category_id = :oldCategoryId")
    suspend fun reassignTransactions(oldCategoryId: Long, newCategoryId: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteHard(categoryId: Long)

    @Query("UPDATE categories SET is_archived = 1 WHERE id = :categoryId")
    suspend fun archive(categoryId: Long)

    // --- Backup/Restore (Phase 8) ---

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Query("DELETE FROM categories")
    suspend fun deleteAllForRestore()
}
