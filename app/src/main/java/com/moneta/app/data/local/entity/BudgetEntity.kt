package com.moneta.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BudgetScope { CATEGORY, OVERALL }
enum class BudgetPeriod { MONTHLY, CUSTOM }

/**
 * A spending limit (Section 13), scoped either to one category or to overall
 * spending. Added in schema v2 via Migration(1, 2) - see MonetaDatabase -
 * never a destructive schema change on top of Phase 1's data (Section 45).
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("category_id")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val scope: BudgetScope,

    /** Null when [scope] == OVERALL. */
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency_code")
    val currencyCode: String,

    val period: BudgetPeriod,

    /** Only used when [period] == CUSTOM; epoch days, inclusive start / inclusive end. */
    @ColumnInfo(name = "custom_period_start")
    val customPeriodStart: Long? = null,

    @ColumnInfo(name = "custom_period_end")
    val customPeriodEnd: Long? = null,

    /** Percentage of the budget at which it's considered "near limit" (Section 13). */
    @ColumnInfo(name = "alert_threshold_percent")
    val alertThresholdPercent: Int = 90,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    /** The user marked this month's budget as finished: what is left of it no longer counts as "yet to spend". */
    @ColumnInfo(name = "is_complete", defaultValue = "0")
    val isComplete: Boolean = false
)
