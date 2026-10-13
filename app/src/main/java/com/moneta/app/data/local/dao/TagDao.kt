package com.moneta.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneta.app.data.local.entity.TagEntity
import com.moneta.app.data.local.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT tag_id FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun tagIdsForTransaction(transactionId: Long): List<Long>

    @Query("""
        SELECT tags.* FROM tags
        INNER JOIN transaction_tags ON tags.id = transaction_tags.tag_id
        WHERE transaction_tags.transaction_id = :transactionId
    """)
    fun observeTagsForTransaction(transactionId: Long): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(crossRef: TransactionTagCrossRef)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun clearTagsForTransaction(transactionId: Long)

    // --- Backup/Restore (Phase 8) ---

    @Query("SELECT * FROM tags")
    suspend fun getAllForBackup(): List<TagEntity>

    @Query("SELECT * FROM transaction_tags")
    suspend fun getAllCrossRefsForBackup(): List<TransactionTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllTags(tags: List<TagEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllCrossRefs(crossRefs: List<TransactionTagCrossRef>)

    @Query("DELETE FROM tags")
    suspend fun deleteAllTagsForRestore()

    @Query("DELETE FROM transaction_tags")
    suspend fun deleteAllCrossRefsForRestore()
}
