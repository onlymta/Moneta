package com.moneta.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moneta.app.data.local.entity.BillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT * FROM bills ORDER BY due_date ASC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills")
    suspend fun getAll(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: Long): BillEntity?

    @Insert
    suspend fun insert(bill: BillEntity): Long

    @Update
    suspend fun update(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)

    // --- Backup/Restore (Phase 8) ---

    @Insert
    suspend fun insertAll(bills: List<BillEntity>): List<Long>

    @Query("DELETE FROM bills")
    suspend fun deleteAllForRestore()
}
