package com.moneta.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BillStatus { UNPAID, PAID }

/**
 * A trackable bill (Section 16). This phase supports manual one-off and
 * "repeat this bill" duplication rather than the full RecurringTransaction
 * engine from Section 15 - see the project README for why that's deferred
 * (idempotent occurrence generation deserves its own careful pass rather
 * than being bolted on here). Added in schema v3 via Migration(2, 3).
 */
@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("account_id"), Index("category_id")]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    @ColumnInfo(name = "due_date")
    val dueDate: Long,

    @ColumnInfo(name = "account_id")
    val accountId: Long? = null,

    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    val status: BillStatus = BillStatus.UNPAID,

    /** Set once this bill is marked paid and a transaction is recorded for it (Section 16). */
    @ColumnInfo(name = "paid_transaction_id")
    val paidTransactionId: Long? = null,

    /** Remind this many days before the due date (0 = on the day); -1 means no reminder. */
    @ColumnInfo(name = "reminder_days_before", defaultValue = "-1")
    val reminderDaysBefore: Int = -1,

    val notes: String? = null
)
