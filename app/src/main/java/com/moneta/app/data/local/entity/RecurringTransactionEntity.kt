package com.moneta.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A recurring transaction template (Section 15). Deliberately supports only
 * DAILY/WEEKLY/MONTHLY/YEARLY with a simple interval count (e.g. every 2
 * weeks) - Section 15's "Custom" frequency (arbitrary rule combinations) is
 * left out rather than half-built, per the project README's stated reason
 * for deferring this feature as long as it was.
 *
 * [nextOccurrenceDate] is the single source of truth for "when does this
 * fire next" and is advanced by RecurringTransactionRepository each time an
 * occurrence is generated - it is never recomputed from scratch on every
 * check, which is what keeps generation idempotent.
 */
@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["to_account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("account_id"), Index("to_account_id"), Index("category_id")]
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "template_type")
    val templateType: TransactionType,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    @ColumnInfo(name = "to_account_id")
    val toAccountId: Long? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    val frequency: RecurrenceFrequency,

    @ColumnInfo(name = "interval_count")
    val intervalCount: Int = 1,

    @ColumnInfo(name = "start_date")
    val startDate: Long,

    @ColumnInfo(name = "end_date")
    val endDate: Long? = null,

    @ColumnInfo(name = "next_occurrence_date")
    val nextOccurrenceDate: Long,

    @ColumnInfo(name = "reminder_enabled")
    val reminderEnabled: Boolean = false,

    @ColumnInfo(name = "reminder_offset_days")
    val reminderOffsetDays: Int = 1,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    val notes: String? = null,
    val payee: String? = null
)
