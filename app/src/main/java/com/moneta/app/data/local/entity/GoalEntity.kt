package com.moneta.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A savings goal (Section 14). [currentAmountMinor] is tracked directly on
 * the goal (incremented by explicit "contribute" actions) rather than
 * derived from transactions, since a goal isn't necessarily tied to any one
 * account or category - Section 14 doesn't require that link, only a
 * target/current/target-date. Added in schema v3 via Migration(2, 3),
 * purely additive per Section 45.
 */
@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_account_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("linked_account_id")]
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val icon: String,

    @ColumnInfo(name = "target_amount_minor")
    val targetAmountMinor: Long,

    @ColumnInfo(name = "current_amount_minor")
    val currentAmountMinor: Long = 0,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    @ColumnInfo(name = "target_date")
    val targetDate: Long? = null,

    @ColumnInfo(name = "linked_account_id")
    val linkedAccountId: Long? = null,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
