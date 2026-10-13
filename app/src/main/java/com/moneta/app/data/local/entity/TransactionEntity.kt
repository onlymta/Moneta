package com.moneta.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * The central financial record (Section 9/32).
 *
 * IMPORTANT: a TRANSFER is always a single row with both [accountId] (the
 * source, debited) and [toAccountId] (the destination, credited) populated,
 * and [categoryId] left null. It must NEVER be modeled as two separate
 * income/expense rows - that would let transfers leak into income/expense
 * totals, which ARCHITECTURE.md Section 11 explicitly forbids.
 *
 * [isDeleted] is a soft-delete flag rather than a hard row delete, so an
 * accidental delete can be undone and so balance-recalculation logic has a
 * single consistent "which rows count" predicate everywhere.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("account_id"),
        Index("to_account_id"),
        Index("category_id"),
        Index("date"),
        Index("type")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val type: TransactionType,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    @ColumnInfo(name = "account_id")
    val accountId: Long,

    /** Only set when [type] == TRANSFER; the destination account credited by this row. */
    @ColumnInfo(name = "to_account_id")
    val toAccountId: Long? = null,

    /** Null when [type] == TRANSFER; every INCOME/EXPENSE row must have a category. */
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    /** Epoch-day local calendar date (see core.date.DateRange) - never a raw timestamp for date math. */
    val date: Long,

    /** Minutes since local midnight, kept separate from [date] so date-range queries stay simple. */
    val time: Int,

    val payee: String? = null,
    val notes: String? = null,

    @ColumnInfo(name = "payment_method")
    val paymentMethod: String? = null,

    @ColumnInfo(name = "receipt_uri")
    val receiptUri: String? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,

    @ColumnInfo(name = "recurring_transaction_id")
    val recurringTransactionId: Long? = null,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
