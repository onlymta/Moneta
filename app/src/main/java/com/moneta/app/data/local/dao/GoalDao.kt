package com.moneta.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneta.app.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals WHERE is_archived = 0 ORDER BY created_at DESC")
    fun observeActive(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): GoalEntity?

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("UPDATE goals SET current_amount_minor = current_amount_minor + :deltaMinor WHERE id = :id")
    suspend fun addContribution(id: Long, deltaMinor: Long)

    @Query("UPDATE goals SET is_archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    // --- Backup/Restore (Phase 8) ---

    @Query("SELECT * FROM goals")
    suspend fun getAllForBackup(): List<GoalEntity>

    @Insert
    suspend fun insertAll(goals: List<GoalEntity>): List<Long>

    @Query("DELETE FROM goals")
    suspend fun deleteAllForRestore()
}
